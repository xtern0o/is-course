package ru.itmo.trace.assignments

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

interface AttachmentStore {
    fun put(content: InputStream): UUID

    fun read(key: UUID): ByteArray
}

@Component
class LocalAttachmentStore(
    @Value("\${trace.attachments.directory:./data/attachments}") directory: String
) : AttachmentStore {
    private val root = Path.of(directory).toAbsolutePath()

    override fun put(content: InputStream): UUID {
        Files.createDirectories(root)
        val key = UUID.randomUUID()
        val target = root.resolve(key.toString())
        // Readers only obtain the key after the attachment metadata transaction commits.
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCompletion(status: Int) {
                    if (status == TransactionSynchronization.STATUS_ROLLED_BACK)
                        Files.deleteIfExists(target)
                }
            }
        )
        Files.copy(content, target)
        return key
    }

    override fun read(key: UUID): ByteArray = Files.readAllBytes(root.resolve(key.toString()))
}
