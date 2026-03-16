package b4.serverview.mixin;

import b4.serverview.accessor.EntityTickAccessor;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Entity.class)
public abstract class EntityMixin implements EntityTickAccessor {
    @Unique
    private boolean serverview$isTicking = true; // Default to true

    @Override
    public void serverview$setTickingTruth(boolean isTicking) {
        this.serverview$isTicking = isTicking;
    }

    @Override
    public boolean serverview$isTickingTruth() {
        return this.serverview$isTicking;
    }
}