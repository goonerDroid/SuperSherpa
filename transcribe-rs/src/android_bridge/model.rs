use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex};

use jni::objects::GlobalRef;
use jni::JNIEnv;

use crate::engines::parakeet::ParakeetEngine;
use crate::TranscriptionEngine;

use super::notifications::notify_status;
use super::state::{
    LoadState, GLOBAL_ENGINE, LOAD_STATE, MODEL_DIRECTORY_OVERRIDE, MODEL_DIR_NAME,
    REQUIRED_MODEL_FILES,
};

pub(super) fn get_engine() -> Option<Arc<Mutex<ParakeetEngine>>> {
    GLOBAL_ENGINE.lock().unwrap().clone()
}

pub(super) fn is_engine_loaded() -> bool {
    GLOBAL_ENGINE.lock().unwrap().is_some()
}

pub(super) fn reset_loaded_engine_state() {
    *GLOBAL_ENGINE.lock().unwrap() = None;
    let (lock, cvar) = &*LOAD_STATE;
    *lock.lock().unwrap() = LoadState::Idle;
    cvar.notify_all();
}

pub(super) fn ensure_loaded_from_thread(
    jvm: &Arc<jni::JavaVM>,
    target_ref: &GlobalRef,
) -> Result<(), String> {
    if is_engine_loaded() {
        if let Ok(mut env) = jvm.attach_current_thread() {
            notify_status(&mut env, target_ref.as_obj(), "Ready");
        }
        return Ok(());
    }

    let (lock, cvar) = &*LOAD_STATE;
    let mut state = lock.lock().unwrap();

    if is_engine_loaded() {
        if let Ok(mut env) = jvm.attach_current_thread() {
            notify_status(&mut env, target_ref.as_obj(), "Ready");
        }
        return Ok(());
    }

    match &*state {
        LoadState::Loading => {
            if let Ok(mut env) = jvm.attach_current_thread() {
                notify_status(&mut env, target_ref.as_obj(), "Waiting for model...");
            }
            while *state == LoadState::Loading {
                state = cvar.wait(state).unwrap();
            }
            drop(state);

            if is_engine_loaded() {
                if let Ok(mut env) = jvm.attach_current_thread() {
                    notify_status(&mut env, target_ref.as_obj(), "Ready");
                }
                Ok(())
            } else {
                let msg = "Model failed to load".to_string();
                if let Ok(mut env) = jvm.attach_current_thread() {
                    notify_status(&mut env, target_ref.as_obj(), &format!("Error: {}", msg));
                }
                Err(msg)
            }
        }
        LoadState::Done => {
            if let Ok(mut env) = jvm.attach_current_thread() {
                notify_status(&mut env, target_ref.as_obj(), "Ready");
            }
            Ok(())
        }
        LoadState::Idle | LoadState::Failed(_) => {
            *state = LoadState::Loading;
            drop(state);

            let result = if let Ok(mut env) = jvm.attach_current_thread() {
                do_load(&mut env, target_ref.as_obj())
            } else {
                Err("Failed to attach JNI thread".to_string())
            };

            let mut state = lock.lock().unwrap();
            match &result {
                Ok(()) => *state = LoadState::Done,
                Err(msg) => *state = LoadState::Failed(msg.clone()),
            }
            cvar.notify_all();
            result
        }
    }
}

pub(super) fn do_load(env: &mut JNIEnv, context: &jni::objects::JObject) -> Result<(), String> {
    let path = resolve_model_dir(env, context).map_err(|e| {
        let msg = format!("Asset error: {}", e);
        notify_status(env, context, &format!("Error: {}", msg));
        msg
    })?;

    notify_status(env, context, "Loading model...");
    let mut eng = ParakeetEngine::new();
    match eng.load_model_with_params(&path, crate::engines::parakeet::ParakeetModelParams::int8()) {
        Ok(_) => {
            *GLOBAL_ENGINE.lock().unwrap() = Some(Arc::new(Mutex::new(eng)));
            notify_status(env, context, "Ready");
            Ok(())
        }
        Err(e) => {
            let marker = path.join(".extraction_complete");
            if marker.exists() {
                let _ = std::fs::remove_file(&marker);
            }
            let msg = format!("Model error: {}", e);
            notify_status(env, context, &format!("Error: {}", msg));
            Err(msg)
        }
    }
}

fn resolve_model_dir(env: &mut JNIEnv, context: &jni::objects::JObject) -> anyhow::Result<PathBuf> {
    if let Some(model_dir) = MODEL_DIRECTORY_OVERRIDE.lock().unwrap().clone() {
        if has_complete_model(&model_dir) {
            return Ok(model_dir);
        }
        log::warn!(
            "Ignoring configured model directory because it is incomplete: {}",
            model_dir.display()
        );
    }

    notify_status(env, context, "Checking assets...");
    extract_assets(env, context)
}

fn has_complete_model(model_dir: &Path) -> bool {
    REQUIRED_MODEL_FILES
        .iter()
        .all(|file_name| model_dir.join(file_name).is_file())
}

fn extract_assets(env: &mut JNIEnv, context: &jni::objects::JObject) -> anyhow::Result<PathBuf> {
    let files_dir_obj = env
        .call_method(context, "getFilesDir", "()Ljava/io/File;", &[])?
        .l()?;
    let path_str_obj = env
        .call_method(
            &files_dir_obj,
            "getAbsolutePath",
            "()Ljava/lang/String;",
            &[],
        )?
        .l()?;
    let path_string: String = env.get_string(&path_str_obj.into())?.into();

    let base_path = PathBuf::from(path_string);
    let model_dir = base_path.join(MODEL_DIR_NAME);
    let marker_file = model_dir.join(".extraction_complete");
    if marker_file.exists() {
        return Ok(model_dir);
    }

    if model_dir.exists() {
        let _ = std::fs::remove_dir_all(&model_dir);
    }

    std::fs::create_dir_all(&model_dir)?;

    let asset_manager_obj = env
        .call_method(
            context,
            "getAssets",
            "()Landroid/content/res/AssetManager;",
            &[],
        )?
        .l()?;
    copy_assets_recursively(env, &asset_manager_obj, MODEL_DIR_NAME, &base_path)?;
    std::fs::write(&marker_file, "ok")?;
    Ok(model_dir)
}

fn copy_assets_recursively(
    env: &mut JNIEnv,
    asset_manager: &jni::objects::JObject,
    path: &str,
    target_root: &PathBuf,
) -> anyhow::Result<()> {
    use jni::objects::JObjectArray;

    let path_jstring = env.new_string(path)?;
    let list_array_obj = env
        .call_method(
            asset_manager,
            "list",
            "(Ljava/lang/String;)[Ljava/lang/String;",
            &[(&path_jstring).into()],
        )?
        .l()?;

    let list_array: JObjectArray = list_array_obj.into();
    let len = env.get_array_length(&list_array)?;

    if len == 0 {
        return copy_asset_file(env, asset_manager, path, target_root);
    }

    let target_dir = target_root.join(path);
    std::fs::create_dir_all(&target_dir)?;

    for i in 0..len {
        let file_name_obj = env.get_object_array_element(&list_array, i)?;
        let file_name: String = env.get_string(&file_name_obj.into())?.into();
        let child_path = if path.is_empty() {
            file_name
        } else {
            format!("{}/{}", path, file_name)
        };
        copy_assets_recursively(env, asset_manager, &child_path, target_root)?;
    }
    Ok(())
}

fn copy_asset_file(
    env: &mut JNIEnv,
    asset_manager: &jni::objects::JObject,
    asset_path: &str,
    target_root: &PathBuf,
) -> anyhow::Result<()> {
    let path_jstring = env.new_string(asset_path)?;
    let result = env.call_method(
        asset_manager,
        "open",
        "(Ljava/lang/String;)Ljava/io/InputStream;",
        &[(&path_jstring).into()],
    );

    match result {
        Ok(stream_val) => {
            let stream_obj = stream_val.l()?;
            let target_file_path = target_root.join(asset_path);
            let mut file = std::fs::File::create(&target_file_path)?;
            let mut buffer = [0u8; 8192];
            let buffer_j = env.new_byte_array(8192)?;

            loop {
                let bytes_read = env
                    .call_method(&stream_obj, "read", "([B)I", &[(&buffer_j).into()])?
                    .i()?;
                if bytes_read == -1 {
                    break;
                }
                let bytes_read_usize = bytes_read as usize;
                let buffer_slice = unsafe {
                    std::slice::from_raw_parts_mut(buffer.as_mut_ptr() as *mut i8, bytes_read_usize)
                };
                env.get_byte_array_region(&buffer_j, 0, buffer_slice)?;
                use std::io::Write;
                file.write_all(&buffer[0..bytes_read_usize])?;
            }

            env.call_method(&stream_obj, "close", "()V", &[])?;
            Ok(())
        }
        Err(_) => Ok(()),
    }
}
