package de.photon.anticheataddition.modules.additions;

import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.attribute.Attributes;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAttributes;
import de.photon.anticheataddition.ServerVersion;
import de.photon.anticheataddition.modules.Module;
import de.photon.anticheataddition.modules.ModuleLoader;
import de.photon.anticheataddition.util.protocol.LivingEntityIdLookup;
import de.photon.anticheataddition.util.protocol.PacketAdapterBuilder;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class DamageIndicator extends Module {
    public static final DamageIndicator INSTANCE = new DamageIndicator();

    private static final float SPOOFED_HEALTH = 0.5F;
    private static final double SPOOFED_MAX_HEALTH = 1.0D;

    private final boolean spoofOthers = loadBoolean(".entities.others", true);
    private final boolean spoofPlayers = loadBoolean(".entities.players", true);

    private final boolean spoofHealth = loadBoolean(".spoof.health", true);
    private final boolean spoofMaxHealth = loadBoolean(".spoof.max_health", true);

    private DamageIndicator()
    {
        super("DamageIndicator");
    }

    @Override
    protected ModuleLoader createModuleLoader()
    {
        return ModuleLoader.of(this, PacketAdapterBuilder
                .of(this, PacketType.Play.Server.ENTITY_METADATA, PacketType.Play.Server.UPDATE_ATTRIBUTES)
                .priority(PacketListenerPriority.HIGH)
                // Use the onSending rather than onSendingRaw to allow for bypassing.
                .onSending((event, user) -> {
                    if (this.spoofHealth && event.getPacketType() == PacketType.Play.Server.ENTITY_METADATA) rewriteHealthMetadata(event);
                    else if (this.spoofMaxHealth && event.getPacketType() == PacketType.Play.Server.UPDATE_ATTRIBUTES) rewriteMaxHealthAttribute(event);
                }).build());
    }

    private void rewriteHealthMetadata(PacketSendEvent event)
    {
        final WrapperPlayServerEntityMetadata wrapper = new WrapperPlayServerEntityMetadata(event);
        final Player viewer = event.getPlayer();
        final int entityId = wrapper.getEntityId();

        // The player should always be able to see their own data.
        if (viewer.getEntityId() == entityId) return;

        final EntityType entityType = LivingEntityIdLookup.INSTANCE.getEntityType(entityId);
        if (!shouldSpoof(entityType)) return;

        final int healthIndex = ServerVersion.ACTIVE.getMetadataPositionIndex().healthIndex();

        // Use raw EntityData here to ensure we can use setValue().
        for (EntityData data : wrapper.getEntityMetadata()) {
            if (data.getIndex() != healthIndex || data.getType() != EntityDataTypes.FLOAT) continue;

            final float health = (Float) data.getValue();
            if (health <= 0.0F) return;

            data.setValue(SPOOFED_HEALTH);
        }
    }

    private void rewriteMaxHealthAttribute(PacketSendEvent event)
    {
        final WrapperPlayServerUpdateAttributes wrapper = new WrapperPlayServerUpdateAttributes(event);
        final Player viewer = event.getPlayer();
        final int entityId = wrapper.getEntityId();

        if (viewer.getEntityId() == entityId) return;

        final EntityType entityType = LivingEntityIdLookup.INSTANCE.getEntityType(entityId);
        if (!shouldSpoof(entityType)) return;

        for (WrapperPlayServerUpdateAttributes.Property property : wrapper.getProperties()) {
            if (property.getAttribute() == Attributes.MAX_HEALTH) {
                property.setValue(SPOOFED_MAX_HEALTH);
                property.setModifiers(new ArrayList<>());
                return;
            }
        }
    }

    private boolean shouldSpoof(EntityType entityType)
    {
        if (entityType == null) return false;

        // Bossbar / boss-health edge cases
        if (entityType == EntityTypes.ENDER_DRAGON || entityType == EntityTypes.WITHER) return false;

        return entityType == EntityTypes.PLAYER ? spoofPlayers : spoofOthers;
    }
}