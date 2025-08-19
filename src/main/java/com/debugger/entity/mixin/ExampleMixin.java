package com.debugger.entity.mixin;

import com.debugger.entity.item.EntityDebugStick;
import com.debugger.entity.screen.EntityDebugScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class ExampleMixin {
	@Inject(method = "interact", at = @At("HEAD"), cancellable = true)
	private void onInteract(Entity entity, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
		PlayerEntity self = (PlayerEntity)(Object)this;
		ItemStack heldItem = self.getStackInHand(hand);

		if (heldItem.getItem() instanceof EntityDebugStick && self.getWorld().isClient) {
			MinecraftClient.getInstance().setScreen(new EntityDebugScreen(entity));
			cir.setReturnValue(ActionResult.PASS); // 彻底阻止Shift+右键的交互
		}
	}
}