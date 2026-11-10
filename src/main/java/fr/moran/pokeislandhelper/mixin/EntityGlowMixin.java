package fr.moran.pokeislandhelper.mixin;

import fr.moran.pokeislandhelper.highlight.Highlight;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fait briller (côté client uniquement) les Pokémon sauvages absents du Pokédex,
 * avec une couleur selon leur état (jamais vu / vu mais pas capturé).
 */
@Mixin(Entity.class)
public abstract class EntityGlowMixin {

    @Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true)
    private void pokeislandhelper$glow(CallbackInfoReturnable<Boolean> cir) {
        if (Highlight.glowColor((Entity) (Object) this) != null) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void pokeislandhelper$color(CallbackInfoReturnable<Integer> cir) {
        Integer color = Highlight.glowColor((Entity) (Object) this);
        if (color != null) {
            cir.setReturnValue(color);
        }
    }
}
