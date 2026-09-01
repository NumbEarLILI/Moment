package com.example.moment.data.nearby

import com.example.moment.domain.nearby.NearbyChatFrame
import java.io.Closeable
import kotlinx.coroutines.flow.Flow

/** 一条已经接通的邻居链路，不关心底下是 TCP 还是蓝牙。 */
interface NearbyLink : Closeable {
    suspend fun send(frame: NearbyChatFrame)

    fun incoming(): Flow<NearbyChatFrame>
}
