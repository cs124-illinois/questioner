package edu.illinois.cs.cs124.stumperd

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoCollection
import org.bson.BsonDocument
import java.util.concurrent.TimeUnit

fun String.collection(collection: String): MongoCollection<BsonDocument> {
    val connectionString = ConnectionString(this)
    val settings = MongoClientSettings.builder()
        .applyConnectionString(connectionString)
        .applyToConnectionPoolSettings { it.maxConnectionIdleTime(Int.MAX_VALUE.toLong(), TimeUnit.MILLISECONDS) }
        .build()
    return MongoClients.create(settings).getDatabase(connectionString.database!!).getCollection(collection, BsonDocument::class.java)
}

fun MongoCollection<BsonDocument>.empty(): MongoCollection<BsonDocument> {
    deleteMany(BsonDocument())
    return this
}
