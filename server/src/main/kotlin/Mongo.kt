package edu.illinois.cs.cs125.questioner.server

import com.mongodb.MongoClient
import com.mongodb.MongoClientOptions
import com.mongodb.MongoClientURI
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

internal val questionerCollection: MongoCollection<BsonDocument> = run {
    require(System.getenv("MONGODB") != null) { "MONGODB environment variable not set" }
    val keystore = System.getenv("KEYSTORE_FILE")
    if (keystore != null) {
        require(System.getenv("KEYSTORE_PASSWORD") != null) { "Must set KEYSTORE_PASSWORD" }
        System.setProperty("javax.net.ssl.trustStore", keystore)
        System.setProperty("javax.net.ssl.trustStorePassword", System.getenv("KEYSTORE_PASSWORD"))
    }
    val collection = System.getenv("MONGODB_COLLECTION") ?: error("Must set MONGODB_COLLECTION")
    val mongoUri = MongoClientURI(System.getenv("MONGODB")!!, MongoClientOptions.builder().sslContext(sslContext))
    val database = mongoUri.database ?: error("MONGODB must specify database to use")
    MongoClient(mongoUri).getDatabase(database).getCollection(collection, BsonDocument::class.java)
}

internal val stumperSolutionCollection: MongoCollection<BsonDocument> = run {
    require(System.getenv("STUMPERDB") != null) { "STUMPERDB environment variable not set" }
    val keystore = System.getenv("KEYSTORE_FILE")
    if (keystore != null) {
        require(System.getenv("KEYSTORE_PASSWORD") != null) { "Must set KEYSTORE_PASSWORD" }
        System.setProperty("javax.net.ssl.trustStore", keystore)
        System.setProperty("javax.net.ssl.trustStorePassword", System.getenv("KEYSTORE_PASSWORD"))
    }
    val mongoUri = MongoClientURI(System.getenv("STUMPERDB")!!, MongoClientOptions.builder().sslContext(sslContext))
    val database = mongoUri.database ?: error("STUMPERDB must specify database to use")
    MongoClient(mongoUri).getDatabase(database).getCollection("solutions", BsonDocument::class.java)
}
