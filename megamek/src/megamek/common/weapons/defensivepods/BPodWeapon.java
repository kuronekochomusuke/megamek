/*
 * MegaMek - Copyright (C) 2004, 2005 Ben Mazur (bmazur@sev.org)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * for more details.
 */
package megamek.common.weapons.defensivepods;

import megamek.common.AmmoType;
import megamek.common.Game;
import megamek.common.Mounted;
import megamek.common.ToHitData;
import megamek.common.actions.WeaponAttackAction;
import megamek.common.weapons.AmmoWeapon;
import megamek.common.weapons.AttackHandler;
import megamek.common.weapons.BPodHandler;
import megamek.server.totalwarfare.TWGameManager;

/**
 * @author Jason Tighe
 * @since Sep 24, 2004
 */
public abstract class BPodWeapon extends AmmoWeapon {
    private static final long serialVersionUID = 654643305102487115L;

    public BPodWeapon() {
        super();
        heat = 0;
        damage = 1;
        ammoType = AmmoType.AmmoTypeEnum.BPOD;
        rackSize = 1;
        minimumRange = 0;
        shortRange = 0;
        mediumRange = 0;
        longRange = 0;
        extremeRange = 0;
        tonnage = 1.0;
        criticals = 1;
        flags = flags.or(F_ONESHOT).or(F_B_POD).or(F_BALLISTIC)
                .or(F_MEK_WEAPON).or(F_TANK_WEAPON);
        explosive = true;
        bv = 2;
        cost = 2500;
        explosionDamage = 2;
        rulesRefs = "204, TM";
        techAdvancement.setTechBase(TechBase.ALL)
                .setIntroLevel(false)
                .setUnofficial(false)
                .setTechRating(TechRating.E)
                .setAvailability(AvailabilityValue.X, AvailabilityValue.X, AvailabilityValue.E, AvailabilityValue.D)
                .setISAdvancement(3068, 3068, 3070, DATE_NONE, DATE_NONE)
                .setISApproximate(true, false, false, false, false)
                .setClanAdvancement(3065, 3068, 3070, DATE_NONE, DATE_NONE)
                .setClanApproximate(true, false, false, false, false)
                .setPrototypeFactions(Faction.CWX, Faction.LC, Faction.WB, Faction.FW)
                .setProductionFactions(Faction.CWX);
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * megamek.common.weapons.Weapon#getCorrectHandler(megamek.common.ToHitData,
     * megamek.common.actions.WeaponAttackAction, megamek.common.Game)
     */
    @Override
    protected AttackHandler getCorrectHandler(ToHitData toHit, WeaponAttackAction waa, Game game,
            TWGameManager manager) {
        return new BPodHandler(toHit, waa, game, manager);
    }

    @Override
    public double getBattleForceDamage(int range, Mounted<?> fcs) {
        return 0;
    }
}
