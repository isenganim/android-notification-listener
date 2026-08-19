package com.saquone.notificationlistener

import android.app.Application
import android.content.Context
import com.saquone.notificationlistener.data.Catalog
import com.saquone.notificationlistener.data.Db
import com.saquone.notificationlistener.data.Outbox
import com.saquone.notificationlistener.data.Settings

class Container(context: Context) {
  private val db = Db.open(context)
  val settings = Settings(context)
  val events = db.events()
  val outbox = Outbox(events, settings)
  val catalog = Catalog(context, settings, db.gateways(), events)
}

class ListenerApp : Application() {
  val container: Container by lazy { Container(this) }
}

val Context.container: Container
  get() = (applicationContext as ListenerApp).container
