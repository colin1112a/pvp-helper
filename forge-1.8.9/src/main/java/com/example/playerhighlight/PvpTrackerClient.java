package com.example.playerhighlight;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side PvP tracker.
 *
 * Stores recent PvP interactions (incoming damage from players, outgoing attacks to players),
 * and provides snapshots for client commands like /lookpvp.
 */
public final class PvpTrackerClient {
    private PvpTrackerClient() {
    }

    private static final int MAX_SESSIONS = 32;
    private static final int MAX_INCOMING_HITS = 24;
    private static final int MAX_OUTGOING_ATTACKS = 24;

    private static final Map<UUID, PvpSession> sessions = new ConcurrentHashMap<UUID, PvpSession>();


    public static void recordIncomingHit(EntityPlayer victim, EntityPlayer attacker, DamageSource source, float amount) {
        if (victim == null || attacker == null) {
            return;
        }

        UUID opponentUuid = attacker.getUniqueID();
        if (opponentUuid == null) {
            return;
        }

        long worldTick = victim.worldObj != null ? victim.worldObj.getTotalWorldTime() : 0L;
        long nowMs = System.currentTimeMillis();

        DistanceSnapshot distance = DistanceSnapshot.capture(attacker, victim);
        String opponentMainHand = describeItem(attacker.getHeldItem());

        Entity direct = source != null ? source.getSourceOfDamage() : null;
        String directTypeId = direct != null ? describeEntityType(direct) : "melee";
        String damageName = source != null ? safeDamageName(source) : "unknown";

        IncomingHit hit = new IncomingHit(worldTick, nowMs, amount, distance, opponentMainHand, damageName, directTypeId);

        PvpSession session = sessions.get(opponentUuid);
        if (session == null) {
            session = new PvpSession(opponentUuid);
            sessions.put(opponentUuid, session);
        }
        session.setOpponentName(attacker.getName());
        session.recordIncoming(hit);
        pruneIfNeeded();
    }

    public static void recordOutgoingAttack(EntityPlayer attacker, EntityPlayer victim) {
        if (attacker == null || victim == null) {
            return;
        }

        UUID opponentUuid = victim.getUniqueID();
        if (opponentUuid == null) {
            return;
        }

        long worldTick = attacker.worldObj != null ? attacker.worldObj.getTotalWorldTime() : 0L;
        long nowMs = System.currentTimeMillis();

        DistanceSnapshot distance = DistanceSnapshot.capture(attacker, victim);
        String myMainHand = describeItem(attacker.getHeldItem());
        OutgoingAttack attack = new OutgoingAttack(worldTick, nowMs, distance, myMainHand);

        PvpSession session = sessions.get(opponentUuid);
        if (session == null) {
            session = new PvpSession(opponentUuid);
            sessions.put(opponentUuid, session);
        }
        session.setOpponentName(victim.getName());
        session.recordOutgoing(attack);
        pruneIfNeeded();
    }


    public static PvpSession getMostRecentSession() {
        PvpSession best = null;
        for (PvpSession session : sessions.values()) {
            if (session == null) {
                continue;
            }
            if (best == null) {
                best = session;
                continue;
            }
            if (session.lastInteractionMs > best.lastInteractionMs) {
                best = session;
                continue;
            }
            if (session.lastInteractionMs == best.lastInteractionMs && session.lastInteractionTick > best.lastInteractionTick) {
                best = session;
            }
        }
        return best;
    }

    public static Collection<PvpSession> getSessionsSnapshot() {
        return new ArrayList<PvpSession>(sessions.values());
    }

    public static void clear() {
        sessions.clear();
    }

    private static void pruneIfNeeded() {
        if (sessions.size() <= MAX_SESSIONS) {
            return;
        }

        List<PvpSession> all = new ArrayList<PvpSession>(sessions.values());
        java.util.Collections.sort(all, new Comparator<PvpSession>() {
            @Override
            public int compare(PvpSession a, PvpSession b) {
                return Long.compare(a.lastInteractionMs, b.lastInteractionMs);
            }
        });
        int removeCount = sessions.size() - MAX_SESSIONS;
        for (int i = 0; i < removeCount && i < all.size(); i++) {
            PvpSession session = all.get(i);
            if (session != null) {
                sessions.remove(session.opponentUuid);
            }
        }
    }


    private static String describeItem(ItemStack stack) {
        if (stack == null) {
            return "empty";
        }
        Item item = stack.getItem();
        if (item == null) {
            return "empty";
        }
        Object name = Item.itemRegistry.getNameForObject(item);
        return name != null ? name.toString() : "unknown";
    }

    private static String describeEntityType(Entity entity) {
        if (entity == null) {
            return "unknown";
        }
        String name = EntityList.getEntityString(entity);
        return name != null ? name : "unknown";
    }

    private static String safeDamageName(DamageSource source) {
        try {
            return source.getDamageType();
        } catch (Throwable t) {
            return "unknown";
        }
    }

    private static void trimToMax(ArrayDeque<?> deque, int maxSize) {
        while (deque.size() > maxSize) {
            deque.removeFirst();
        }
    }


    public static final class PvpSession {
        public final UUID opponentUuid;
        private String opponentName = "Unknown";

        public long lastInteractionTick = 0L;
        public long lastInteractionMs = 0L;

        public int incomingHitCountTotal = 0;
        public int outgoingAttackCountTotal = 0;

        private final ArrayDeque<IncomingHit> incomingHits = new ArrayDeque<IncomingHit>();
        private final ArrayDeque<OutgoingAttack> outgoingAttacks = new ArrayDeque<OutgoingAttack>();

        PvpSession(UUID opponentUuid) {
            this.opponentUuid = opponentUuid;
        }

        public String getOpponentName() {
            return opponentName;
        }

        public void setOpponentName(String opponentName) {
            if (opponentName != null && !opponentName.trim().isEmpty()) {
                this.opponentName = opponentName;
            }
        }

        public List<IncomingHit> getIncomingHitsNewestFirst() {
            List<IncomingHit> list = new ArrayList<IncomingHit>(incomingHits);
            java.util.Collections.sort(list, new Comparator<IncomingHit>() {
                @Override
                public int compare(IncomingHit a, IncomingHit b) {
                    int c = Long.compare(b.worldTick, a.worldTick);
                    if (c != 0) return c;
                    return Long.compare(b.timeMs, a.timeMs);
                }
            });
            return list;
        }

        public List<OutgoingAttack> getOutgoingAttacksNewestFirst() {
            List<OutgoingAttack> list = new ArrayList<OutgoingAttack>(outgoingAttacks);
            java.util.Collections.sort(list, new Comparator<OutgoingAttack>() {
                @Override
                public int compare(OutgoingAttack a, OutgoingAttack b) {
                    int c = Long.compare(b.worldTick, a.worldTick);
                    if (c != 0) return c;
                    return Long.compare(b.timeMs, a.timeMs);
                }
            });
            return list;
        }


        void recordIncoming(IncomingHit hit) {
            if (hit == null) {
                return;
            }
            incomingHitCountTotal += 1;
            lastInteractionTick = hit.worldTick;
            lastInteractionMs = hit.timeMs;
            incomingHits.addLast(hit);
            trimToMax(incomingHits, MAX_INCOMING_HITS);
        }

        void recordOutgoing(OutgoingAttack attack) {
            if (attack == null) {
                return;
            }
            outgoingAttackCountTotal += 1;
            lastInteractionTick = attack.worldTick;
            lastInteractionMs = attack.timeMs;
            outgoingAttacks.addLast(attack);
            trimToMax(outgoingAttacks, MAX_OUTGOING_ATTACKS);
        }
    }

    public static final class IncomingHit {
        public final long worldTick;
        public final long timeMs;
        public final float damageAmount;
        public final DistanceSnapshot distance;
        public final String opponentMainHandItemId;
        public final String damageName;
        public final String directSourceTypeId;

        IncomingHit(long worldTick, long timeMs, float damageAmount, DistanceSnapshot distance,
                    String opponentMainHandItemId, String damageName, String directSourceTypeId) {
            this.worldTick = worldTick;
            this.timeMs = timeMs;
            this.damageAmount = damageAmount;
            this.distance = distance;
            this.opponentMainHandItemId = opponentMainHandItemId;
            this.damageName = damageName;
            this.directSourceTypeId = directSourceTypeId;
        }
    }

    public static final class OutgoingAttack {
        public final long worldTick;
        public final long timeMs;
        public final DistanceSnapshot distance;
        public final String myMainHandItemId;

        OutgoingAttack(long worldTick, long timeMs, DistanceSnapshot distance, String myMainHandItemId) {
            this.worldTick = worldTick;
            this.timeMs = timeMs;
            this.distance = distance;
            this.myMainHandItemId = myMainHandItemId;
        }
    }


    public static final class DistanceSnapshot {
        public final double centerDistance;
        public final double horizontalDistance;
        public final double eyeDistance;
        public final double boxDistance;
        public final double eyeToTargetBoxDistance;

        private DistanceSnapshot(double centerDistance, double horizontalDistance, double eyeDistance, double boxDistance,
                                 double eyeToTargetBoxDistance) {
            this.centerDistance = centerDistance;
            this.horizontalDistance = horizontalDistance;
            this.eyeDistance = eyeDistance;
            this.boxDistance = boxDistance;
            this.eyeToTargetBoxDistance = eyeToTargetBoxDistance;
        }

        public static DistanceSnapshot capture(Entity a, Entity b) {
            if (a == null || b == null) {
                return new DistanceSnapshot(Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
            }

            Vec3 aPos = a.getPositionVector();
            Vec3 bPos = b.getPositionVector();
            double dx = aPos.xCoord - bPos.xCoord;
            double dz = aPos.zCoord - bPos.zCoord;

            double center = aPos.distanceTo(bPos);
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            Vec3 aEye = new Vec3(a.posX, a.posY + a.getEyeHeight(), a.posZ);
            Vec3 bEye = new Vec3(b.posX, b.posY + b.getEyeHeight(), b.posZ);
            double eye = aEye.distanceTo(bEye);
            double box = distanceBetweenBoxes(a.getEntityBoundingBox(), b.getEntityBoundingBox());
            double eyeToBox = distancePointToBox(aEye, b.getEntityBoundingBox());
            return new DistanceSnapshot(center, horizontal, eye, box, eyeToBox);
        }


        private static double distanceBetweenBoxes(AxisAlignedBB a, AxisAlignedBB b) {
            if (a == null || b == null) {
                return Double.NaN;
            }

            double dx = axisDistance(a.minX, a.maxX, b.minX, b.maxX);
            double dy = axisDistance(a.minY, a.maxY, b.minY, b.maxY);
            double dz = axisDistance(a.minZ, a.maxZ, b.minZ, b.maxZ);
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        private static double distancePointToBox(Vec3 point, AxisAlignedBB box) {
            if (point == null || box == null) {
                return Double.NaN;
            }
            double dx = axisDistancePoint(point.xCoord, box.minX, box.maxX);
            double dy = axisDistancePoint(point.yCoord, box.minY, box.maxY);
            double dz = axisDistancePoint(point.zCoord, box.minZ, box.maxZ);
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        private static double axisDistancePoint(double p, double min, double max) {
            if (p < min) {
                return min - p;
            }
            if (p > max) {
                return p - max;
            }
            return 0.0;
        }

        private static double axisDistance(double aMin, double aMax, double bMin, double bMax) {
            if (aMax < bMin) {
                return bMin - aMax;
            }
            if (bMax < aMin) {
                return aMin - bMax;
            }
            return 0.0;
        }
    }
}
