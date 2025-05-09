/*
 * MegaMek - Copyright (C) 2016 The MegaMek Team
 *
 *  This program is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License as published by the Free
 *  Software Foundation; either version 2 of the License, or (at your option)
 *  any later version.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 *  for more details.
 */
package megamek.client.ratgenerator;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import megamek.common.Compute;
import megamek.common.EntityMovementMode;
import megamek.common.MekSummary;
import megamek.common.annotations.Nullable;
import megamek.logging.MMLogger;

/**
 * Manages random assignment table generated by RATGenerator.
 *
 * @author Neoancient
 */
public class UnitTable {
    private final static MMLogger LOGGER = MMLogger.create(UnitTable.class);

    @FunctionalInterface
    public interface UnitFilter {
        boolean include(MekSummary ms);
    }

    private static final int CACHE_SIZE = 32;

    private static final LinkedHashMap<Parameters, UnitTable> cache = new LinkedHashMap<>(CACHE_SIZE, 0.75f, true) {

        @Serial
        private static final long serialVersionUID = -8016095510116134800L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<Parameters, UnitTable> entry) {
            return size() >= CACHE_SIZE;
        }
    };

    /**
     * Checks the cache for a previously generated table meeting the criteria. If none is found, generates it and adds
     * it to the cache. This method is provided as a convenience for when there are no excluded roles.
     *
     * @param faction          The faction the table filters for
     * @param unitType         {@link megamek.common.UnitType} constant with the type of unit
     * @param year             the game year
     * @param rating           the unit's equipment rating; if null, the table is not adjusted for unit rating.
     * @param weightClasses    a collection of {@link megamek.common.EntityWeightClass} constants to include in the
     *                         table; if null or empty all weight classes are included
     * @param networkMask      One of the {@link ModelRecord} NETWORK constants, for filtering various C3 systems
     * @param movementModes    the movement modes covered by the table, null/empty for all modes
     * @param roles            {@link MissionRole} types the units should qualify for, null/empty for no role filtering
     * @param roleStrictness   how strongly to apply roles, zero for none/minimal with higher being more restrictive;
     *                         typically no higher than 5
     * @param deployingFaction when using the salvage/isorla mechanism, any omni unit will select the configuration
     *                         based on the faction actually deploying
     *
     * @return table containing units which fit all parameters and their relative weights
     */
    public static UnitTable findTable(FactionRecord faction, int unitType, int year, String rating,
          Collection<Integer> weightClasses, int networkMask, Collection<EntityMovementMode> movementModes,
          Collection<MissionRole> roles, int roleStrictness, FactionRecord deployingFaction) {
        Objects.requireNonNull(faction);
        Parameters params = new Parameters(faction,
              unitType,
              year,
              rating,
              weightClasses,
              networkMask,
              movementModes,
              roles,
              new ArrayList<>(),
              roleStrictness,
              deployingFaction);
        return findTable(params);
    }

    /**
     * Overloaded method, with additional argument for excluded roles
     *
     * @param faction          The faction the table filters for
     * @param unitType         {@link megamek.common.UnitType} constant with the type of unit
     * @param year             the game year
     * @param rating           the unit's equipment rating; if null, the table is not adjusted for unit rating.
     * @param weightClasses    collection of {@link megamek.common.EntityWeightClass} constants to include in the table;
     *                         if null or empty all weight classes are included
     * @param networkMask      One of the {@link ModelRecord} NETWORK constants, for filtering various C3 systems
     * @param movementModes    the movement modes covered by the table, null/empty for all modes
     * @param roles            {@link MissionRole} types the units should qualify for, null/empty for no role filtering
     * @param rolesExcluded    {@link MissionRole} types the units should not contain, null empty to allow all roles
     * @param roleStrictness   how strongly to apply roles, zero for none/minimal with higher being more restrictive;
     *                         typically no higher than 5
     * @param deployingFaction when using the salvage/isorla mechanism, any omni unit will select the configuration
     *                         based on the faction actually deploying
     *
     * @return table containing units which fit all parameters and their relative weights
     */
    public static UnitTable findTable(FactionRecord faction, int unitType, int year, String rating,
          Collection<Integer> weightClasses, int networkMask, Collection<EntityMovementMode> movementModes,
          Collection<MissionRole> roles, Collection<MissionRole> rolesExcluded, int roleStrictness,
          FactionRecord deployingFaction) {
        Objects.requireNonNull(faction);
        Parameters params = new Parameters(faction,
              unitType,
              year,
              rating,
              weightClasses,
              networkMask,
              movementModes,
              roles,
              rolesExcluded,
              roleStrictness,
              deployingFaction);
        return findTable(params);
    }

    /**
     * Provided as a convenience to call findTable while using the faction parameter as the deploying faction
     */

    public static UnitTable findTable(FactionRecord faction, int unitType, int year, String rating,
          Collection<Integer> weightClasses, int networkMask, Collection<EntityMovementMode> movementModes,
          Collection<MissionRole> roles, int roleStrictness) {
        return findTable(faction,
              unitType,
              year,
              rating,
              weightClasses,
              networkMask,
              movementModes,
              roles,
              roleStrictness,
              faction);
    }

    /**
     * Checks cache for a unit table with the given parameters. If none is found, generates one and adds to the cache
     * using a copy of the Parameters object as a key.
     *
     * @param params - the parameters to use in generating the table.
     *
     * @return a generated table matching the parameters
     */
    public static synchronized UnitTable findTable(Parameters params) {
        Objects.requireNonNull(params);
        UnitTable retVal = cache.get(params);
        if (retVal == null) {
            retVal = new UnitTable(params);
            if (retVal.hasUnits()) {
                // Use a copy of the params for the cache key to prevent changing it.
                cache.put(params.copy(), retVal);
            }
        }
        return retVal;
    }

    private final Parameters key;
    private final List<TableEntry> salvageTable = new ArrayList<>();
    private final List<TableEntry> unitTable = new ArrayList<>();

    int salvageTotal;
    int unitTotal;
    /*
     * Filtering can reduce the total weight of the units. Calculate the salvage pct
     * when
     * creating the table to maintain the same proportion.
     */ int salvagePct;

    /**
     * Initializes table based on restrictions provided by a Parameters object
     *
     * @param key a {@link Parameters} structure providing the parameters for generating the table
     */
    protected UnitTable(Parameters key) {
        this.key = key;
        /*
         * Generate the RAT, then go through it to build the NavigableMaps that
         * will be used for random selection.
         */
        if (key.getFaction() != null) {

            // Simple check if the faction is active now
            if (key.getFaction().isActiveInYear(key.getYear())) {

                List<TableEntry> table = RATGenerator.getInstance()
                                               .generateTable(key.getFaction(),
                                                     key.getUnitType(),
                                                     key.getYear(),
                                                     key.getRating(),
                                                     key.getWeightClasses(),
                                                     key.getNetworkMask(),
                                                     key.getMovementModes(),
                                                     key.getRoles(),
                                                     key.getRoleStrictness(),
                                                     key.getDeployingFaction());
                Collections.sort(table);

                table.forEach(te -> {
                    if (te.isUnit()) {
                        unitTotal += te.weight;
                        unitTable.add(te);
                    } else {
                        salvageTotal += te.weight;
                        salvageTable.add(te);
                    }
                });
                if (salvageTotal + unitTotal > 0) {
                    salvagePct = salvageTotal * 100 / (salvageTotal + unitTotal);
                }
            }
        } else {
            LOGGER.warn("key.getFaction() returned null; likely faction lookup error");
        }
    }

    /**
     * @return - number of entries in the table
     */

    public int getNumEntries() {
        return salvageTable.size() + unitTable.size();
    }

    /**
     * @param index index of the {@link TableEntry}
     *
     * @return - the entry at the indicated index
     */
    private TableEntry getEntry(int index) {
        try {
            if (index < salvageTable.size()) {
                return salvageTable.get(index);
            }
            return unitTable.get(index - salvageTable.size());
        } catch (IndexOutOfBoundsException e) {
            // Can't log from a static context
        }
        return null;
    }

    /**
     * @param index index of the Weight
     *
     * @return - the weight value for the entry at the indicated index
     */
    public int getEntryWeight(int index) {
        TableEntry te = getEntry(index);
        return (te != null) ? te.weight : 0;
    }

    /**
     * @param index index of the Text
     *
     * @return - a string representing the entry at the indicated index for use in the table
     */
    public String getEntryText(int index) {
        if (!unitTable.isEmpty() && index >= salvageTable.size()) {
            return unitTable.get(index - salvageTable.size()).getUnitEntry().getName();
        } else {
            if (key.getFaction().isClan()) {
                return "Isorla: " + salvageTable.get(index).getSalvageFaction().getName(key.getYear() - 5);
            } else {
                return "Salvage: " + salvageTable.get(index).getSalvageFaction().getName(key.getYear() - 5);
            }
        }
    }

    /**
     * @param index index of the Tech Base
     *
     * @return - a string representing the entry at the indicated index for use in the table
     */
    public String getTechBase(int index) {
        if (!unitTable.isEmpty() && index >= salvageTable.size()) {
            return unitTable.get(index - salvageTable.size()).getUnitEntry().isClan() ? "Clan" : "IS";
        } else {
            return key.getFaction().isClan() ? "Clan" : "IS";
        }
    }

    /**
     * @param index index of the Unit Role
     *
     * @return - a string representing the entry at the indicated index for use in the table
     */
    public String getUnitRole(int index) {
        if (!unitTable.isEmpty() && index >= salvageTable.size()) {
            return unitTable.get(index - salvageTable.size()).getUnitEntry().getRole().toString();
        } else {
            return "";
        }
    }

    /**
     * @param index of the {@link MekSummary}
     *
     * @return - the MekSummary entry for the indicated index, or null if this is a salvage entry
     */
    public MekSummary getMekSummary(int index) {
        if (!unitTable.isEmpty() && index >= salvageTable.size()) {
            return unitTable.get(index - salvageTable.size()).getUnitEntry();
        }
        return null;
    }

    /**
     * @param index of the BV
     *
     * @return - the BV of the unit at the indicated index, or 0 if this is a salvage entry
     */
    public int getBV(int index) {
        if (!unitTable.isEmpty() && index >= salvageTable.size()) {
            return unitTable.get(index - salvageTable.size()).getUnitEntry().getBV();
        } else {
            return 0;
        }
    }

    /**
     * @return true if the generated table has any unit entries
     */
    public boolean hasUnits() {
        return !unitTable.isEmpty();
    }

    /**
     * Selects a unit from the full table.
     *
     * @return the selected unit
     */
    public MekSummary generateUnit() {
        return generateUnit(null);
    }

    /**
     * Selects a unit from the entries in the table that pass the filter
     *
     * @param filter - the function that determines which units are permitted; if null, no filter is applied.
     *
     * @return - the selected unit, or null if no units pass the filter.
     */
    public @Nullable MekSummary generateUnit(UnitFilter filter) {
        int roll = Compute.randomInt(100);
        if (roll < salvagePct) {
            MekSummary ms = generateSalvage(filter);
            if (ms != null) {
                return ms;
            }
        }
        List<TableEntry> useUnitList = unitTable;
        int unitMapSize = unitTotal;
        if (filter != null) {
            useUnitList = unitTable.stream().filter(te -> filter.include(te.getUnitEntry())).toList();
            unitMapSize = useUnitList.stream().mapToInt(te -> te.weight).sum();
        }

        if (unitMapSize > 0) {
            roll = Compute.randomInt(unitMapSize);
            for (TableEntry te : useUnitList) {
                if (roll < te.weight) {
                    return te.getUnitEntry();
                }
                roll -= te.weight;
            }
        }
        return null;
    }

    /**
     * Selects a number of units from the table.
     *
     * @param num - the number of units to be generated.
     *
     * @return - a list of randomly generated units
     */
    public ArrayList<MekSummary> generateUnits(int num) {
        return generateUnits(num, null);
    }

    /**
     * Selects a number of units from the table with a filter.
     *
     * @param num    - the number of units to be generated.
     * @param filter - the function that determines which units are permitted; if null, no filter is applied.
     *
     * @return - a list of randomly generated units
     */
    public ArrayList<MekSummary> generateUnits(int num, UnitFilter filter) {
        ArrayList<MekSummary> retVal = new ArrayList<>();
        for (int i = 0; i < num; i++) {
            MekSummary ms = generateUnit(filter);
            if (ms != null) {
                retVal.add(ms);
            }
        }
        return retVal;
    }

    /**
     * Selects a faction from the salvage list and generates a table using the same parameters as this table, but from
     * five years earlier. Generated tables are cached for later use. If the generated table contains no units, it is
     * discarded and the selected entry is deleted. This continues until either a unit is generated or there are no
     * remaining entries.
     *
     * @param filter - passed to generateUnit() in the generated table.
     *
     * @return - a unit generated from another faction, or null if none of the factions in the salvage list contain any
     *       units that meet the parameters.
     */
    private @Nullable MekSummary generateSalvage(UnitFilter filter) {
        while (salvageTotal > 0) {
            int roll = Compute.randomInt(salvageTotal);
            TableEntry salvageEntry = null;
            for (TableEntry te : salvageTable) {
                if (roll < te.weight) {
                    salvageEntry = te;
                    break;
                }
                roll -= te.weight;
            }
            if (salvageEntry != null) {
                UnitTable salvage = UnitTable.findTable(salvageEntry.getSalvageFaction(),
                      key.getUnitType(),
                      key.getYear() - 5,
                      key.getRating(),
                      key.getWeightClasses(),
                      key.getNetworkMask(),
                      key.getMovementModes(),
                      key.getRoles(),
                      key.getRoleStrictness(),
                      key.getFaction());
                if (salvage.hasUnits()) {
                    return salvage.generateUnit(filter);
                } else {
                    salvageTotal -= salvageEntry.weight;
                    salvageTable.remove(salvageEntry);
                }
            }
        }
        return null;
    }

    /*
     * A tuple that contains either a salvage or a faction entry along with its
     * relative weight.
     * in the table.
     */
    public static class TableEntry implements Comparable<TableEntry> {
        int weight;
        Object entry;

        public TableEntry(int weight, Object entry) {
            this.weight = weight;
            this.entry = entry;
        }

        public MekSummary getUnitEntry() {
            return (MekSummary) entry;
        }

        public FactionRecord getSalvageFaction() {
            return (FactionRecord) entry;
        }

        public boolean isUnit() {
            return entry instanceof MekSummary;
        }

        @Override
        public int compareTo(TableEntry other) {
            if (entry instanceof MekSummary && other.entry instanceof FactionRecord) {
                return 1;
            }
            if (entry instanceof FactionRecord && other.entry instanceof MekSummary) {
                return -1;
            }
            return toString().compareTo(other.toString());
        }

        @Override
        public String toString() {
            if (entry instanceof MekSummary) {
                return ((MekSummary) entry).getName();
            }
            return entry.toString();
        }
    }

}
