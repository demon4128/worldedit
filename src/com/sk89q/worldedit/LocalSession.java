// $Id$
/*
 * WorldEdit
 * Copyright (C) 2010 sk89q <http://www.sk89q.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package com.sk89q.worldedit;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import com.sk89q.worldedit.snapshots.Snapshot;
import com.sk89q.worldedit.tools.BrushTool;
import com.sk89q.worldedit.tools.SinglePickaxe;
import com.sk89q.worldedit.tools.BlockTool;
import com.sk89q.worldedit.tools.Tool;
import com.sk89q.worldedit.bags.BlockBag;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.regions.CuboidRegion;

/**
 * An instance of this represents the WorldEdit session of a user. A session
 * stores history and settings. Sessions are not tied particularly to any
 * player and can be shuffled between players, saved, and loaded.
 *
 * @author sk89q
 */
public class LocalSession {
    /**
     * List of compass modes.
     */
    public enum CompassMode {
        JUMPTO,
        THRU
    }
    
    public static int MAX_HISTORY_SIZE = 15;
    
    private LocalConfiguration config;
    
    private boolean placeAtPos1 = false;
    private Vector pos1, pos2;
    private Region region;
    private LinkedList<EditSession> history = new LinkedList<EditSession>();
    private int historyPointer = 0;
    private CuboidClipboard clipboard;
    private boolean toolControl = true;
    private boolean superPickaxe = false;
    private BlockTool pickaxeMode = new SinglePickaxe();
    private Map<Integer, Tool> tools
            = new HashMap<Integer, Tool>();
    private int maxBlocksChanged = -1;
    private boolean useInventory;
    private Snapshot snapshot;
    private String lastScript;
    private CompassMode compassMode = CompassMode.JUMPTO;
    private boolean beenToldVersion = false;
    
    /**
     * Construct the object.
     * 
     * @param config
     */
    public LocalSession(LocalConfiguration config) {
        this.config = config;
    }

    /**
     * Clear history.
     */
    public void clearHistory() {
        history.clear();
        historyPointer = 0;
    }

    /**
     * Remember an edit session for the undo history. If the history maximum
     * size is reached, old edit sessions will be discarded.
     * 
     * @param editSession
     */
    public void remember(EditSession editSession) {
        // Don't store anything if no changes were made
        if (editSession.size() == 0) { return; }

        // Destroy any sessions after this undo point
        while (historyPointer < history.size()) {
            history.remove(historyPointer);
        }
        history.add(editSession);
        while (history.size() > MAX_HISTORY_SIZE) {
            history.remove(0);
        }
        historyPointer = history.size();
    }

    /**
     * Performs an undo.
     *
     * @param newBlockBag
     * @return whether anything was undone
     */
    public EditSession undo(BlockBag newBlockBag) {
        historyPointer--;
        if (historyPointer >= 0) {
            EditSession editSession = history.get(historyPointer);
            EditSession newEditSession =
                    new EditSession(editSession.getWorld(), -1, newBlockBag);
            editSession.undo(newEditSession);
            return editSession;
        } else {
            historyPointer = 0;
            return null;
        }
    }

    /**
     * Performs a redo
     *
     * @param newBlockBag
     * @return whether anything was redone
     */
    public EditSession redo(BlockBag newBlockBag) {
        if (historyPointer < history.size()) {
            EditSession editSession = history.get(historyPointer);
            EditSession newEditSession =
                new EditSession(editSession.getWorld(), -1, newBlockBag);
            editSession.redo(newEditSession);
            historyPointer++;
            return editSession;
        }

        return null;
    }

    /**
     * Checks to make sure that position 1 is defined.
     * 
     * @throws IncompleteRegionException
     */
    private void checkPos1() throws IncompleteRegionException {
        if (pos1 == null) {
            throw new IncompleteRegionException();
        }
    }

    /**
     * Checks to make sure that position 2 is defined.
     *
     * @throws IncompleteRegionException
     */
    private void checkPos2() throws IncompleteRegionException {
        if (pos2 == null) {
            throw new IncompleteRegionException();
        }
    }

    /**
     * Returns true if the region is fully defined.
     * 
     * @return 
     */
    public boolean isRegionDefined() {
        return pos1 != null && pos2 != null;
    }

    /**
     * Gets defined position 1.
     * 
     * @return position 1
     * @throws IncompleteRegionException
     */
    public Vector getPos1() throws IncompleteRegionException {
        checkPos1();
        return pos1;
    }

    /**
     * Sets position 1.
     *
     * @param pt
     */
    public void setPos1(Vector pt) {
        pos1 = pt;
        if (pos1 != null && pos2 != null) {
            region = new CuboidRegion(pos1, pos2);
        }
    }

    /**
     * Gets position 2.
     * 
     * @return position 2
     * @throws IncompleteRegionException
     */
    public Vector getPos2() throws IncompleteRegionException {
        checkPos2();
        return pos2;
    }

    /**
     * Sets position 2.
     *
     * @param pt
     */
    public void setPos2(Vector pt) {
        pos2 = pt;
        if (pos1 != null && pos2 != null) {
            region = new CuboidRegion(pos1, pos2);
        }
    }

    /**
     * Update session position 1/2 based on the currently set region,
     * provided that the region is of a cuboid.
     */
    public void learnRegionChanges() {
        if (region instanceof CuboidRegion) {
            CuboidRegion cuboidRegion = (CuboidRegion)region;
            pos1 = cuboidRegion.getPos1();
            pos2 = cuboidRegion.getPos2();
        }
    }

    /**
     * Get the region. If you change the region, you should
     * call learnRegionChanges().
     * 
     * @return region
     * @throws IncompleteRegionException
     */
    public Region getRegion() throws IncompleteRegionException {
        if (region == null) {
            throw new IncompleteRegionException();
        }
        return region;
    }

    /**
     * Gets the clipboard.
     * 
     * @return clipboard, may be null
     * @throws EmptyClipboardException
     */
    public CuboidClipboard getClipboard() throws EmptyClipboardException {
        if (clipboard == null) {
            throw new EmptyClipboardException();
        }
        return clipboard;
    }

    /**
     * Sets the clipboard.
     * 
     * @param clipboard
     */
    public void setClipboard(CuboidClipboard clipboard) {
        this.clipboard = clipboard;
    }

    /**
     * See if tool control is enabled.
     * 
     * @return true if enabled
     */
    public boolean isToolControlEnabled() {
        return toolControl;
    }

    /**
     * Change tool control setting.
     * 
     * @param toolControl
     */
    public void setToolControl(boolean toolControl) {
        this.toolControl = toolControl;
    }

    /**
     * Get the maximum number of blocks that can be changed in an edit session.
     *
     * @return block change limit
     */
    public int getBlockChangeLimit() {
        return maxBlocksChanged;
    }

    /**
     * Set the maximum number of blocks that can be changed.
     * 
     * @param maxBlocksChanged
     */
    public void setBlockChangeLimit(int maxBlocksChanged) {
        this.maxBlocksChanged = maxBlocksChanged;
    }

    /**
     * Checks whether the super pick axe is enabled.
     * 
     * @return status
     */
    public boolean hasSuperPickAxe() {
        return superPickaxe;
    }

    /**
     * Enable super pick axe.
     */
    public void enableSuperPickAxe() {
        superPickaxe = true;
    }

    /**
     * Disable super pick axe.
     */
    public void disableSuperPickAxe() {
        superPickaxe = false;
    }

    /**
     * Toggle the super pick axe.
     *
     * @return status
     */
    public boolean toggleSuperPickAxe() {
        superPickaxe = !superPickaxe;
        return superPickaxe;
    }

    /**
     * Get the placement position.
     * 
     * @param player 
     * @return position
     * @throws IncompleteRegionException
     */
    public Vector getPlacementPosition(LocalPlayer player)
            throws IncompleteRegionException {
        if (!placeAtPos1) {
            return player.getBlockIn();
        }

        checkPos1();
        return pos1;
    }

    /**
     * Toggle placement position.
     * 
     * @return 
     */
    public boolean togglePlacementPosition() {
        placeAtPos1 = !placeAtPos1;
        return placeAtPos1;
    }
    
    /**
     * Get a block bag for a player.
     * 
     * @param player
     * @return
     */
    public BlockBag getBlockBag(LocalPlayer player) {
        if (!useInventory) {
            return null;
        }
        return player.getInventoryBlockBag();
    }

    /**
     * Get the snapshot that has been selected.
     * 
     * @return the snapshot
     */
    public Snapshot getSnapshot() {
        return snapshot;
    }

    /**
     * Select a snapshot.
     * 
     * @param snapshot
     */
    public void setSnapshot(Snapshot snapshot) {
        this.snapshot = snapshot;
    }

    /**
     * @return the superPickaxeMode
     */
    public BlockTool getSuperPickaxe() {
        return pickaxeMode;
    }

    /**
     * Set the super pickaxe tool.
     * 
     * @param tool
     */
    public void setSuperPickaxe(BlockTool tool) {
        this.pickaxeMode = tool;
    }

    /**
     * Get the tool assigned to the item.
     * 
     * @param item 
     * @return the tool
     */
    public Tool getTool(int item) {
        return tools.get(item);
    }

    /**
     * Get the brush tool assigned to the item. If there is no tool assigned
     * or the tool is not assigned, the slot will be replaced with the
     * brush tool.
     * 
     * @param item 
     * @return the tool
     * @throws InvalidToolBindException 
     */
    public BrushTool getBrushTool(int item) throws InvalidToolBindException {
        Tool tool = getTool(item);
        
        if (tool == null || !(tool instanceof BrushTool)) {
            tool = new BrushTool();
            setTool(item, tool);
        }
        
        return (BrushTool)tool;
    }

    /**
     * Set the tool.
     * 
     * @param item 
     * @param tool the tool to set
     * @throws InvalidToolBindException 
     */
    public void setTool(int item, Tool tool) throws InvalidToolBindException {
        if (item > 0 && item < 255) {
            throw new InvalidToolBindException(item, "Blocks can't be used");
        } else if (item == 263 || item == 348) {
            throw new InvalidToolBindException(item, "Item is not usuable");
        } else if (item == config.wandItem) {
            throw new InvalidToolBindException(item, "Already used for the wand");
        } else if (item == config.navigationWand) {
            throw new InvalidToolBindException(item, "Already used for the navigation wand");
        }
        
        this.tools.put(item, tool);
    }

    /**
     * Returns whether inventory usage is enabled for this session.
     * 
     * @return the useInventory
     */
    public boolean isUsingInventory() {
        return useInventory;
    }

    /**
     * Set the state of inventory usage.
     * 
     * @param useInventory the useInventory to set
     */
    public void setUseInventory(boolean useInventory) {
        this.useInventory = useInventory;
    }

    /**
     * Get the last script used.
     * 
     * @return the lastScript
     */
    public String getLastScript() {
        return lastScript;
    }

    /**
     * Set the last script used.
     * 
     * @param lastScript the lastScript to set
     */
    public void setLastScript(String lastScript) {
        this.lastScript = lastScript;
    }

    /**
     * Get the compass mode.
     * 
     * @return the compassMode
     */
    public CompassMode getCompassMode() {
        return compassMode;
    }

    /**
     * Set the compass mode.
     * 
     * @param compassMode the compassMode to set
     */
    public void setCompassMode(CompassMode compassMode) {
        this.compassMode = compassMode;
    }
    
    /**
     * Tell the player the WorldEdit version.
     * 
     * @param player 
     */
    public void tellVersion(LocalPlayer player) {
        if (config.showFirstUseVersion) {
            if (!beenToldVersion) {
                player.printRaw("\u00A78WorldEdit ver. " + WorldEdit.getVersion()
                        + " (http://sk89q.com/projects/worldedit/)");
                beenToldVersion = true;
            }
        }
    }
}
