package io.stillpage.app.ui.book.import.remote

import android.app.Application
import io.stillpage.app.base.BaseViewModel
import io.stillpage.app.data.appDb
import io.stillpage.app.data.entities.Server

class ServersViewModel(application: Application): BaseViewModel(application) {


    fun delete(server: Server) {
        execute {
            appDb.serverDao.delete(server)
        }
    }

}