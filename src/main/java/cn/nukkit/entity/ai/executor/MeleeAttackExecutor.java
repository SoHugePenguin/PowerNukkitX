package cn.nukkit.entity.ai.executor;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.api.PowerNukkitXOnly;
import cn.nukkit.api.Since;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityCanAttack;
import cn.nukkit.entity.EntityIntelligent;
import cn.nukkit.entity.ai.memory.EntityMemory;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.inventory.EntityInventoryHolder;
import cn.nukkit.item.Item;
import cn.nukkit.item.MinecraftItemID;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.protocol.EntityEventPacket;

import java.util.EnumMap;
import java.util.Map;

/**
 * 通用近战攻击执行器.
 * <p>
 * Universal melee attack actuator.
 */
@PowerNukkitXOnly
@Since("1.6.0.0-PNX")
public class MeleeAttackExecutor extends AboutControlExecutor {

    protected Class<? extends EntityMemory<?>> memoryClazz;
    protected float speed;
    protected int maxSenseRangeSquared;
    protected boolean clearDataWhenLose;
    protected int coolDown;

    protected int attackTick;

    protected Vector3 oldTarget;
    /**
     * 用来指定特定的攻击目标.
     * <p>
     * Used to specify a specific attack target.
     */
    @Since("1.19.30-r1")
    protected Entity target;
    /**
     * 用来指定特定的视线目标
     * <p>
     * Used to specify a specific look target.
     */
    @Since("1.19.30-r1")
    protected Vector3 lookTarget;

    /**
     * 近战攻击执行器
     *
     * @param memoryClazz       记忆
     * @param speed             移动向攻击目标的速度
     * @param maxSenseRange     最大获取攻击目标范围
     * @param clearDataWhenLose 失去目标时清空memoryClazz对应的记忆
     * @param coolDown          攻击冷却时间(单位tick)
     */
    public MeleeAttackExecutor(Class<? extends EntityMemory<?>> memoryClazz, float speed, int maxSenseRange, boolean clearDataWhenLose, int coolDown) {
        this.memoryClazz = memoryClazz;
        this.speed = speed;
        this.maxSenseRangeSquared = maxSenseRange * maxSenseRange;
        this.clearDataWhenLose = clearDataWhenLose;
        this.coolDown = coolDown;
    }

    @Override
    public boolean execute(EntityIntelligent entity) {
        attackTick++;
        if (!entity.isEnablePitch()) entity.setEnablePitch(true);

        if (this.target == null) {
            //获取目标
            if (entity.getBehaviorGroup().getMemoryStorage().isEmpty(memoryClazz)) return false;
            target = entity.getBehaviorGroup().getMemoryStorage().get(memoryClazz).getData();
        }

        //如果是玩家检测模式 检查距离 检查是否在同一维度
        if ((target instanceof Player player && !player.isSurvival()) || entity.distanceSquared(target) > maxSenseRangeSquared
                || !entity.level.getName().equals(target.level.getName())) return false;

        if (entity.getMovementSpeed() != speed) entity.setMovementSpeed(speed);

        Vector3 clonedTarget = target.clone();
        if (this.lookTarget == null) {
            lookTarget = clonedTarget;
        }
        //更新寻路target
        setRouteTarget(entity, clonedTarget);
        //更新视线target
        setLookTarget(entity, lookTarget);

        var floor = clonedTarget.floor();

        if (oldTarget == null || !oldTarget.equals(floor)) entity.getBehaviorGroup().setForceUpdateRoute(true);

        oldTarget = floor;

        if (entity.distanceSquared(target) <= 3.5 && attackTick > coolDown) {
            Item item = entity instanceof EntityInventoryHolder holder ? holder.getItemInHand() : Item.fromString(MinecraftItemID.AIR.getNamespacedId());

            float defaultDamage = 0;
            if (entity instanceof EntityCanAttack entityCanAttack) {
                defaultDamage = entityCanAttack.getDiffHandDamage(entity.getServer().getDifficulty());
            }
            float itemDamage = item.getAttackDamage() + defaultDamage;

            Enchantment[] enchantments = item.getEnchantments();
            if (item.applyEnchantments()) {
                for (Enchantment enchantment : enchantments) {
                    itemDamage += enchantment.getDamageBonus(target);
                }
            }

            Map<EntityDamageEvent.DamageModifier, Float> damage = new EnumMap<>(EntityDamageEvent.DamageModifier.class);
            damage.put(EntityDamageEvent.DamageModifier.BASE, itemDamage);

            float knockBack = 0.3f;
            if (item.applyEnchantments()) {
                Enchantment knockBackEnchantment = item.getEnchantment(Enchantment.ID_KNOCKBACK);
                if (knockBackEnchantment != null) {
                    knockBack += knockBackEnchantment.getLevel() * 0.1f;
                }
            }

            EntityDamageByEntityEvent ev = new EntityDamageByEntityEvent(entity, target, EntityDamageEvent.DamageCause.ENTITY_ATTACK, damage, knockBack, item.applyEnchantments() ? enchantments : null);

            ev.setBreakShield(item.canBreakShield());

            target.attack(ev);
            playAttackAnimation(entity);
            attackTick = 0;
            if (target.getHealth() == 0) return false;
        }
        return true;
    }

    @Override
    public void onStop(EntityIntelligent entity) {
        removeRouteTarget(entity);
        removeLookTarget(entity);
        //重置速度
        entity.setMovementSpeed(0.1f);
        if (clearDataWhenLose) {
            entity.getBehaviorGroup().getMemoryStorage().clear(memoryClazz);
        }
        entity.setEnablePitch(false);
        this.target = null;
        this.lookTarget = null;
    }

    @Override
    public void onInterrupt(EntityIntelligent entity) {
        removeRouteTarget(entity);
        removeLookTarget(entity);
        //重置速度
        entity.setMovementSpeed(0.1f);
        if (clearDataWhenLose) {
            entity.getBehaviorGroup().getMemoryStorage().clear(memoryClazz);
        }
        entity.setEnablePitch(false);
        this.target = null;
        this.lookTarget = null;
    }

    protected void playAttackAnimation(EntityIntelligent entity) {
        EntityEventPacket pk = new EntityEventPacket();
        pk.eid = entity.getId();
        pk.event = EntityEventPacket.ARM_SWING;
        Server.broadcastPacket(entity.getViewers().values(), pk);
    }
}
