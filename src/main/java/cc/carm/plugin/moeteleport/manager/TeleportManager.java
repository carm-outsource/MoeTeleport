package cc.carm.plugin.moeteleport.manager;

import cc.carm.plugin.moeteleport.Main;
import cc.carm.plugin.moeteleport.conf.PluginConfig;
import cc.carm.plugin.moeteleport.conf.PluginMessages;
import cc.carm.plugin.moeteleport.conf.location.DataLocation;
import cc.carm.plugin.moeteleport.teleport.TeleportQueue;
import cc.carm.plugin.moeteleport.teleport.TeleportRequest;
import cc.carm.plugin.moeteleport.teleport.target.TeleportLocationTarget;
import cc.carm.plugin.moeteleport.teleport.target.TeleportTarget;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.Nullable;
import xyz.xenondevs.particle.ParticleBuilder;
import xyz.xenondevs.particle.ParticleEffect;
import xyz.xenondevs.particle.data.texture.ItemTexture;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TeleportManager {

    protected final Map<UUID, TeleportQueue> teleportQueue = new ConcurrentHashMap<>();
    protected BukkitRunnable runnable;

    public TeleportManager(Main main) {
        this.runnable = new BukkitRunnable() {
            @Override
            public void run() {
                tickQueue();
            }
        };
        this.runnable.runTaskTimerAsynchronously(main, 20L, 20L);
    }

    public void shutdown() {
        if (!this.runnable.isCancelled()) {
            this.runnable.cancel();
        }
    }

    public void tickQueue() {
        boolean enableEffect = PluginConfig.TELEPORTATION.EFFECTS.getNotNull();

        Iterator<Map.Entry<UUID, TeleportQueue>> queueIterator = teleportQueue.entrySet().iterator();
        while (queueIterator.hasNext()) {
            Map.Entry<UUID, TeleportQueue> entry = queueIterator.next();
            TeleportQueue queue = entry.getValue();
            if (!queue.getPlayer().isOnline()) { // 玩家已经离线，无需继续执行判断
                queueIterator.remove();
                continue;
            }
            if (!queue.checkTime()) {
                PluginConfig.TELEPORTATION.SOUND.CHANNELING.playTo(queue.getPlayer());
                PluginConfig.TELEPORTATION.TITLE.CHANNELING.send(
                        queue.getPlayer(),
                        queue.getRemainSeconds() + 1, queue.getTarget().getText()
                );

                if (enableEffect) {
                    new ParticleBuilder(ParticleEffect.PORTAL, queue.getPlayer().getLocation())
                            .setAmount(100).setOffsetY(1F).display();
                }

                continue;
            }

            queueIterator.remove();
            executeTeleport(queue);
        }
    }

    public void interruptQueue(TeleportQueue queue) {
        teleportQueue.remove(queue.getPlayer().getUniqueId());
        PluginMessages.TELEPORT.INTERRUPTED.send(queue.getPlayer());
        PluginConfig.TELEPORTATION.SOUND.INTERRUPTED.playTo(queue.getPlayer());
    }

    public TeleportQueue getQueue(UUID uuid) {
        return teleportQueue.get(uuid);
    }

    public TeleportQueue getQueue(Player player) {
        return getQueue(player.getUniqueId());
    }

    public void clearQueue(UUID uuid) {
        teleportQueue.remove(uuid);
    }

    public boolean isChanneling(UUID uuid) {
        return teleportQueue.containsKey(uuid);
    }

    public boolean isChanneling(Player player) {
        return isChanneling(player.getUniqueId());
    }

    public @Nullable Duration getDelayDuration() {
        return Duration.of(PluginConfig.TELEPORTATION.WAIT_TIME.getNotNull(), ChronoUnit.SECONDS);
    }

    public void queueTeleport(@Nullable TeleportQueue queue) {
        if (queue == null) return;
        if (queue.checkTime()) { // 直接满足传送条件
            executeTeleport(queue);
            return;
        }
        teleportQueue.put(queue.getPlayer().getUniqueId(), queue);
    }

    public void queueTeleport(TeleportRequest request) {
        queueTeleport(request.createQueue(getDelayDuration()));
    }

    public void queueTeleport(Player player, TeleportTarget target) {
        queueTeleport(new TeleportQueue(player, target, getDelayDuration()));
    }

    public void queueTeleport(Player player, DataLocation target) {
        queueTeleport(player, new TeleportLocationTarget(target));
    }

    public void queueTeleport(Player player, Location target) {
        queueTeleport(player, new TeleportLocationTarget(target));
    }

    protected void executeTeleport(TeleportQueue queue) {
        Player player = queue.getPlayer();
        queue.getUser().setLastLocation(player.getLocation());

        Location location = queue.getTarget().prepare();
        if (location == null) {
            PluginMessages.TELEPORT.NOT_AVAILABLE.send(player, queue.getTarget().getText());
            PluginConfig.TELEPORTATION.SOUND.FAILED.playTo(player);
        } else {
            PluginMessages.TELEPORT.TELEPORTED.send(player, queue.getTarget().getText());
            PluginConfig.TELEPORTATION.TITLE.TELEPORTED.send(player, queue.getTarget().getText());
            Main.getInstance().getScheduler().run(() -> {
                player.teleport(location);
                PluginConfig.TELEPORTATION.SOUND.TELEPORTED.playTo(player);
            });


            if (PluginConfig.TELEPORTATION.EFFECTS.getNotNull()) {
                new ParticleBuilder(ParticleEffect.ITEM_CRACK, location)
                        .setParticleData(new ItemTexture(new ItemStack(Material.ENDER_EYE)))
                        .setAmount(1).setOffsetY(1F).display();
            }
        }
    }
}
