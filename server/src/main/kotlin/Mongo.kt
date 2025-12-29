package edu.illinois.cs.cs125.questioner.server

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoCollection
import org.bson.BsonDocument
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

private val sslContext = SSLContext.getInstance("SSL").apply {
    init(
        null,
        arrayOf(object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate>? = null
            override fun checkClientTrusted(certs: Array<X509Certificate?>?, authType: String?) {}
            override fun checkServerTrusted(certs: Array<X509Certificate?>?, authType: String?) {}
        }),
        SecureRandom(),
    )
}

internal fun createMongoCollection(
    connectionString: String,
    collectionName: String,
    sslContext: SSLContext? = null,
): MongoCollection<BsonDocument> {
    val connString = ConnectionString(connectionString)
    val settingsBuilder = MongoClientSettings.builder()
        .applyConnectionString(connString)
    if (sslContext != null) {
        settingsBuilder.applyToSslSettings { it.context(sslContext) }
    }
    val database = connString.database ?: error("Connection string must specify database")
    return MongoClients.create(settingsBuilder.build())
        .getDatabase(database)
        .getCollection(collectionName, BsonDocument::class.java)
}

internal val questionerCollection: MongoCollection<BsonDocument> by lazy {
    require(System.getenv("MONGODB") != null) { "MONGODB environment variable not set" }
    val keystore = System.getenv("KEYSTORE_FILE")
    if (keystore != null) {
        require(System.getenv("KEYSTORE_PASSWORD") != null) { "Must set KEYSTORE_PASSWORD" }
        System.setProperty("javax.net.ssl.trustStore", keystore)
        System.setProperty("javax.net.ssl.trustStorePassword", System.getenv("KEYSTORE_PASSWORD"))
    }
    val collectionName = System.getenv("MONGODB_COLLECTION") ?: error("Must set MONGODB_COLLECTION")
    createMongoCollection(System.getenv("MONGODB")!!, collectionName, sslContext)
}
