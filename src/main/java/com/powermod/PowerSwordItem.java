package com.powermod;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;

public class PowerSwordItem extends SwordItem {
    private static final float COST = 20f;
    private static final DustParticleEffect BLACK = new DustParticleEffect(new Vector3f(0.04f, 0.04f, 0.04f), 1.6f);
    private static final DustParticleEffect RED = new DustParticleEffect(new Vector3f(1.0f, 0.05f, 0.05f), 1.8f);
    private static final DustParticleEffect GOLD = new DustParticleEffect(new Vector3f(1.0f, 0.65f, 0.1f), 0.8f);

    public PowerSwordItem(ToolMaterial material, int damage, float speed, Settings settings) {
        super(material, damage, speed, settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) return TypedActionResult.success(stack);

        ServerPlayerEntity player = (ServerPlayerEntity) user;
        ServerWorld sw = (ServerWorld) world;

        float mana = PowerMod.getMana(player);
        if (mana < COST) {
            player.sendMessage(Text.literal("พลังไม่พอ!"), true);
            return TypedActionResult.fail(stack);
        }
        PowerMod.setMana(player, mana - COST);
        PowerMod.syncMana(player);

        Vec3d look = user.getRotationVec(1f).normalize();
        Vec3d start = user.getEyePos();
        Set<LivingEntity> hit = new HashSet<>();

        // ring on the ground around the player
        for (int a = 0; a < 360; a += 15) {
            double rad = Math.toRadians(a);
            sw.spawnParticles(RED, user.getX() + Math.cos(rad) * 2.2, user.getY() + 0.1, user.getZ() + Math.sin(rad) * 2.2, 1, 0, 0.05, 0, 0.02);
        }

        // energy wave travelling forward
        for (int i = 1; i <= 14; i++) {
            Vec3d pos = start.add(look.multiply(i * 1.2));
            sw.spawnParticles(RED, pos.x, pos.y, pos.z, 8, 0.5, 0.5, 0.5, 0.01);
            sw.spawnParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, 5, 0.4, 0.4, 0.4, 0.06);
            sw.spawnParticles(GOLD, pos.x, pos.y, pos.z, 10, 0.6, 0.6, 0.6, 0.15);   // sparks
            sw.spawnParticles(BLACK, pos.x, pos.y, pos.z, 6, 0.7, 0.7, 0.7, 0.08);   // black debris
            hit.addAll(sw.getEntitiesByClass(LivingEntity.class, new Box(pos, pos).expand(1.6), e -> e != user));
        }

        for (LivingEntity e : hit) {
            e.damage(sw.getDamageSources().playerAttack(player), 10f);
            e.setOnFireFor(3);
            e.takeKnockback(0.8, -look.x, -look.z);
        }

        sw.playSound(null, user.getBlockPos(), SoundEvents.ENTITY_WITHER_SHOOT, SoundCategory.PLAYERS, 0.8f, 1.3f);
        ServerPlayNetworking.send(player, PowerMod.FX_FLASH, PacketByteBufs.create());
        user.getItemCooldownManager().set(this, 15);
        return TypedActionResult.success(stack);
    }
}
