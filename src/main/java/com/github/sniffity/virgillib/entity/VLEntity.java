package com.github.sniffity.virgillib.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

public class VLEntity extends PathfinderMob {

    public static final EntityDataAccessor<BlockPos> TARGET_POS = SynchedEntityData.defineId(VLEntity.class, EntityDataSerializers.BLOCK_POS);

    protected VLEntity(EntityType<? extends PathfinderMob> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
    }

    @Override
    public void defineSynchedData(){
        this.entityData.define(TARGET_POS,new BlockPos(0,0,0));
    }

    public BlockPos getTargetPos(){
        return this.entityData.get(TARGET_POS);
    }

    public void setTargetPos(BlockPos targetPos){
        this.entityData.set(TARGET_POS,targetPos);
    }
}