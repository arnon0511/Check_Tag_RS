package com.tskforging.checktagrs

import android.content.Context
import android.provider.Settings
import androidx.work.Worker
import androidx.work.WorkerParameters

class CentralSyncWorker(context:Context,params:WorkerParameters):Worker(context,params){
    override fun doWork():Result{
        val db=EvidenceDb(applicationContext)
        val deviceId=Settings.Secure.getString(applicationContext.contentResolver,Settings.Secure.ANDROID_ID) ?: "UNKNOWN"
        var retry=false
        for(id in db.pendingSyncIds()){
            db.markSyncing(id)
            val result=try{CentralApi.post(db.buildSyncPayload(id,deviceId))}catch(error:Throwable){kotlin.Result.failure(error)}
            if(result.isSuccess)db.markSynced(id) else {db.markPending(id,result.exceptionOrNull()?.message ?: "Unknown error");retry=true}
        }
        db.close()
        return if(retry)Result.retry() else Result.success()
    }
}
