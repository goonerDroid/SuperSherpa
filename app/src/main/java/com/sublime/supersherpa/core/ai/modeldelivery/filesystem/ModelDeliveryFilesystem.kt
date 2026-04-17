package com.sublime.supersherpa.core.ai.modeldelivery.filesystem

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal fun cleanupDirectory(directory: File) {
    if (directory.exists()) {
        directory.deleteRecursively()
    }
}

internal fun movePath(
    source: File,
    destination: File,
    replaceExisting: Boolean = false,
) {
    destination.parentFile?.mkdirs()

    try {
        if (replaceExisting) {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } else {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
            )
        }
    } catch (_: AtomicMoveNotSupportedException) {
        if (replaceExisting) {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        } else {
            Files.move(
                source.toPath(),
                destination.toPath(),
            )
        }
    }
}
