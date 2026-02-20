package net.minecraft.world.level.gameevent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.debug.DebugGameEventInfo;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.Vec3;

public class GameEventDispatcher {

    private final ServerLevel level;

    public GameEventDispatcher(ServerLevel level) {
        this.level = level;
    }

    public void post(Holder<GameEvent> gameEvent, Vec3 position, GameEvent.Context context) {
        int i = ((GameEvent) gameEvent.value()).notificationRadius();
        BlockPos blockpos = BlockPos.containing(position);
        int j = SectionPos.blockToSectionCoord(blockpos.getX() - i);
        int k = SectionPos.blockToSectionCoord(blockpos.getY() - i);
        int l = SectionPos.blockToSectionCoord(blockpos.getZ() - i);
        int i1 = SectionPos.blockToSectionCoord(blockpos.getX() + i);
        int j1 = SectionPos.blockToSectionCoord(blockpos.getY() + i);
        int k1 = SectionPos.blockToSectionCoord(blockpos.getZ() + i);
        List<GameEvent.ListenerInfo> list = new ArrayList();
        GameEventListenerRegistry.ListenerVisitor gameeventlistenerregistry_listenervisitor = (gameeventlistener, vec31) -> {
            if (gameeventlistener.getDeliveryMode() == GameEventListener.DeliveryMode.BY_DISTANCE) {
                list.add(new GameEvent.ListenerInfo(gameEvent, position, context, gameeventlistener, vec31));
            } else {
                gameeventlistener.handleGameEvent(this.level, gameEvent, context, position);
            }

        };
        boolean flag = false;

        for (int l1 = j; l1 <= i1; ++l1) {
            for (int i2 = l; i2 <= k1; ++i2) {
                ChunkAccess chunkaccess = this.level.getChunkSource().getChunkNow(l1, i2);

                if (chunkaccess != null) {
                    for (int j2 = k; j2 <= j1; ++j2) {
                        flag |= chunkaccess.getListenerRegistry(j2).visitInRangeListeners(gameEvent, position, context, gameeventlistenerregistry_listenervisitor);
                    }
                }
            }
        }

        if (!list.isEmpty()) {
            this.handleGameEventMessagesInQueue(list);
        }

        if (flag) {
            this.level.debugSynchronizers().broadcastEventToTracking(BlockPos.containing(position), DebugSubscriptions.GAME_EVENTS, new DebugGameEventInfo(gameEvent, position));
        }

    }

    private void handleGameEventMessagesInQueue(List<GameEvent.ListenerInfo> listenerInfos) {
        Collections.sort(listenerInfos);

        for (GameEvent.ListenerInfo gameevent_listenerinfo : listenerInfos) {
            GameEventListener gameeventlistener = gameevent_listenerinfo.recipient();

            gameeventlistener.handleGameEvent(this.level, gameevent_listenerinfo.gameEvent(), gameevent_listenerinfo.context(), gameevent_listenerinfo.source());
        }

    }
}
