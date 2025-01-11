package edu.illinois.cs.cs124.stumperd

import com.mongodb.MongoClient
import com.mongodb.MongoClientOptions
import com.mongodb.MongoClientURI
import com.mongodb.client.MongoCollection
import org.bson.BsonDocument

fun String.collection(collection: String): MongoCollection<BsonDocument> = MongoClientURI(this, MongoClientOptions.builder().maxConnectionIdleTime(Int.MAX_VALUE))
    .let { uri ->
        MongoClient(uri).getDatabase(uri.database!!).getCollection(collection, BsonDocument::class.java)
    }

fun MongoCollection<BsonDocument>.empty(): MongoCollection<BsonDocument> {
    deleteMany(BsonDocument())
    return this
}
