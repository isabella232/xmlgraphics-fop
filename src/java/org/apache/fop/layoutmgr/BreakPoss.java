/*
 * Copyright 1999-2005 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* $Id$ */

package org.apache.fop.layoutmgr;

import org.apache.fop.traits.LayoutProps;
import org.apache.fop.traits.MinOptMax;

/**
 * Represents a break possibility for the layout manager.
 * Used to pass information between different levels of layout manager concerning
 * the break positions. In an inline context, before and after are interpreted as
 * start and end.
 * The position field is opaque represents where the break occurs. It is a
 * nested structure with one level for each layout manager involved in generating
 * the BreakPoss..
 * @author Karen Lease
 */
public class BreakPoss {

    // --- Values for flags returned from lower level LM. ---
    /** May break after */
    public static final int CAN_BREAK_AFTER         = 0x01;
    /** Last area generated by FO */
    public static final int ISLAST                  = 0x02;
    /** First area generated by FO */
    public static final int ISFIRST                 = 0x04;
    /** Forced break (ie LF) */
    public static final int FORCE                   = 0x08;
    /** May break before */
    public static final int CAN_BREAK_BEFORE        = 0x10;
    /** has anchors */
    public static final int HAS_ANCHORS             = 0x40;
    /**
     * Set this flag if all fo:character generated Areas would
     * suppressed at the end or beginning of a line */
    public static final int ALL_ARE_SUPPRESS_AT_LB  = 0x80;
    /** This break possibility is a hyphenation */
    public static final int HYPHENATED              = 0x100;
    /**
     * If this break possibility ends the line, all remaining characters
     * in the lowest level text LM will be suppressed.
     */
    public static final int REST_ARE_SUPPRESS_AT_LB = 0x200;
    /** Next area for LM overflows */
    public static final int NEXT_OVERFLOWS          = 0x400;

    /** The opaque position object used by m_lm to record its
     *  break position.
     */
    private Position position;

    /**
     * The size range in the stacking direction of the area which would be
     * generated if this BreakPoss were used.
     */
    private MinOptMax stackSize;

    /**
     * Max height above and below the baseline. These are cumulative.
     */
    private int lead;
    // the max height of before and after alignment
    private int total;
    // middle alignment height for after
    private int middle;

    /** Size in the non-stacking direction (perpendicular). */
    private MinOptMax nonStackSize;

    private long flags = 0;
    private LayoutProps layoutProps = new LayoutProps();

    /** Store space-after (or end) and space-before (or start) to be
     * added if this break position is used.
     */
    private SpaceSpecifier spaceSpecTrailing;
    private SpaceSpecifier spaceSpecLeading;

    public BreakPoss(Position position) {
        this(position, 0);
    }

    public BreakPoss(Position position, long flags) {
        this.position = position;
        this.flags = flags;
    }

    /**
     * The top-level layout manager responsible for this break
     */
    public LayoutManager getLayoutManager() {
        return position.getLM();
    }

    //     public void setLayoutManager(LayoutManager lm) {
    //         m_lm = lm;
    //     }

    /**
     * An object representing the break position in this layout manager.
     */
    public Position getPosition() {
        return position;
    }

    public void setPosition(Position pos) {
        position = pos;
    }

    public void setStackingSize(MinOptMax size) {
        this.stackSize = size;
    }

    public MinOptMax getStackingSize() {
        return this.stackSize ;
    }

    public void setNonStackingSize(MinOptMax size) {
        this.nonStackSize = size;
    }

    public MinOptMax getNonStackingSize() {
        return this.nonStackSize ;
    }

    public long getFlags() {
        return flags;
    }

    public void setFlag(int flagBit) {
        setFlag(flagBit, true);
    }

    public void setFlag(int flagBit, boolean bSet) {
        if (bSet) {
            flags |= flagBit;
        } else {
            flags &= ~flagBit;
        }
    }

    public boolean isLastArea() {
        return ((flags & ISLAST) != 0);
    }

    public boolean isFirstArea() {
        return ((flags & ISFIRST) != 0);
    }

    public boolean canBreakAfter() {
        return ((flags & CAN_BREAK_AFTER) != 0);
    }

    public boolean canBreakBefore() {
        return ((flags & CAN_BREAK_BEFORE) != 0);
    }

    public boolean couldEndLine() {
        return ((flags & REST_ARE_SUPPRESS_AT_LB) != 0);
    }

    public boolean isForcedBreak() {
        return ((flags & FORCE) != 0);
    }

    public boolean nextBreakOverflows() {
        return ((flags & NEXT_OVERFLOWS) != 0);
    }

    public boolean isSuppressible() {
        return ((flags & ALL_ARE_SUPPRESS_AT_LB) != 0);
    }

    public SpaceSpecifier getLeadingSpace() {
        return spaceSpecLeading;
    }

    public MinOptMax resolveLeadingSpace() {
        if (spaceSpecLeading != null) {
            return spaceSpecLeading.resolve(false);
        } else {
            return new MinOptMax(0);
        }
    }

    public SpaceSpecifier getTrailingSpace() {
        return spaceSpecTrailing;
    }

    public MinOptMax resolveTrailingSpace(boolean bEndsRefArea) {
        if (spaceSpecTrailing != null) {
            return spaceSpecTrailing.resolve(bEndsRefArea);
        } else {
            return new MinOptMax(0);
        }
    }


    public void setLeadingSpace(SpaceSpecifier spaceSpecLeading) {
        this.spaceSpecLeading = spaceSpecLeading;
    }

    public void setTrailingSpace(SpaceSpecifier spaceSpecTrailing) {
        this.spaceSpecTrailing = spaceSpecTrailing;
    }

    public LayoutProps getLayoutProps() {
        return layoutProps;
    }

    public int getLead() {
        return lead;
    }

    public int getTotal() {
        return total;
    }

    public int getMiddle() {
        return middle;
    }

    /**
     * set lead height of baseline positioned element
     * @param ld new lead value
     */
    public void setLead(int ld) {
        lead = ld;
    }

    /**
     * Set total height of top or bottom aligned element
     * @param t new total height
     */
    public void setTotal(int t) {
        total = t;
    }

    /**
     * Set distance below baseline of middle aligned element
     * @param t new value
     */
    public void setMiddle(int t) {
        middle = t;
    }
    
    /** @see java.lang.Object#toString() */
    public String toString() {
        StringBuffer sb = new StringBuffer("BreakPoss");
        if (isFirstArea()) {
            sb.append(", first area");
        }
        if (isLastArea()) {
            sb.append(", last area");
        }
        if (isForcedBreak()) {
            sb.append(", forced break");
        }
        sb.append(", stackSize={");
        sb.append(stackSize);
        sb.append("}, pos=");
        sb.append(position);
        return sb.toString();
    }
}
