/*
 * $Id$
 * Copyright (C) 2001 The Apache Software Foundation. All rights reserved.
 * For details on use and redistribution please refer to the
 * LICENSE file included with these sources.
 */

package org.apache.fop.layout;

// fop
import org.apache.fop.render.Renderer;
import org.apache.fop.messaging.MessageHandler;
import org.apache.fop.layout.inline.*;
import org.apache.fop.datatypes.IDNode;
import org.apache.fop.fo.properties.WrapOption;
import org.apache.fop.fo.properties.WhiteSpaceCollapse;
import org.apache.fop.fo.properties.TextAlign;
import org.apache.fop.fo.properties.TextAlignLast;
import org.apache.fop.fo.properties.LeaderPattern;
import org.apache.fop.fo.properties.Hyphenate;
import org.apache.fop.fo.properties.CountryMaker;
import org.apache.fop.fo.properties.LanguageMaker;
import org.apache.fop.fo.properties.LeaderAlignment;
import org.apache.fop.fo.properties.VerticalAlign;
import org.apache.fop.layout.hyphenation.Hyphenation;
import org.apache.fop.layout.hyphenation.Hyphenator;
import org.apache.fop.configuration.Configuration;

// java
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.awt.Rectangle;

public class LineArea extends Area {

    protected int lineHeight;
    protected int halfLeading;
    protected int nominalFontSize;
    protected int nominalGlyphHeight;

    protected int allocationHeight;
    protected int startIndent;
    protected int endIndent;

    private int placementOffset;

    private FontState currentFontState;    // not the nominal, which is
    // in this.fontState
    private float red, green, blue;
    private int wrapOption;
    private int whiteSpaceCollapse;
    int vAlign;

    /* hyphenation */
    HyphenationProps hyphProps;

    /*
     * the width of text that has definitely made it into the line
     * area
     */
    protected int finalWidth = 0;

    /* the position to shift a link rectangle in order to compensate for links embedded within a word */
    protected int embeddedLinkStart = 0;

    /* the width of the current word so far */
    // protected int wordWidth = 0;

    /* values that prev (below) may take */
    protected static final int NOTHING = 0;
    protected static final int WHITESPACE = 1;
    protected static final int TEXT = 2;
    protected static final int MULTIBYTECHAR = 3;

    /* the character type of the previous character */
    protected int prev = NOTHING;

    /* the position in data[] of the start of the current word */
    // protected int wordStart;

    /* the length (in characters) of the current word */
    // protected int wordLength = 0;

    /* width of spaces before current word */
    protected int spaceWidth = 0;

    /*
     * the inline areas that have not yet been added to the line
     * because subsequent characters to come (in a different addText)
     * may be part of the same word
     */
    protected ArrayList pendingAreas = new ArrayList();

    /* the width of the pendingAreas */
    /* public for problem check in AbstractRenderer */
    public int pendingWidth = 0;

    /* text-decoration of the previous text */
    protected boolean prevUlState = false;
    protected boolean prevOlState = false;
    protected boolean prevLTState = false;

    public class Leader {
        int leaderPattern;
        int leaderLengthMinimum;
        int leaderLengthOptimum;
        int leaderLengthMaximum;
        int ruleStyle;
        int ruleThickness;
        int leaderPatternWidth;
        int leaderAlignment;
        FontState fontState;
        float red;
        float green;
        float blue;
        int placementOffset;
        int position;

        Leader(int leaderPattern, int leaderLengthMinimum,
               int leaderLengthOptimum, int leaderLengthMaximum,
               int ruleStyle, int ruleThickness,
               int leaderPatternWidth, int leaderAlignment,
               FontState fontState,
               float red, float green, float blue,
               int placementOffset,
               int position) {
            this.leaderPattern=leaderPattern;
            this.leaderLengthMinimum=leaderLengthMinimum;
            this.leaderLengthOptimum=leaderLengthOptimum;
            this.leaderLengthMaximum=leaderLengthMaximum;
            this.ruleStyle=ruleStyle;
            this.ruleThickness=ruleThickness;
            this.leaderPatternWidth=leaderPatternWidth;
            this.leaderAlignment=leaderAlignment;
            this.fontState=fontState;
            this.red=red;
            this.green=green;
            this.blue=blue;
            this.placementOffset=placementOffset;
            this.position = position;
        }
        void expand(int leaderLength) {
            char dot = '.';
            int dotWidth =  getCharWidth(dot);
            char space = ' ';
            int spaceWidth = getCharWidth(space);
            int idx=children.indexOf(this);
            children.remove(this);
            switch (leaderPattern) {
            case LeaderPattern.SPACE:
                InlineSpace spaceArea = new InlineSpace(leaderLength,false);
                children.add(idx,spaceArea);
                break;
            case LeaderPattern.RULE:
                LeaderArea leaderArea = new LeaderArea(this.fontState, this.red, this.green,
                                                       this.blue, "", leaderLength,
                                                       this.leaderPattern,
                                                       this.ruleThickness, this.ruleStyle);
                leaderArea.setYOffset(this.placementOffset);
                children.add(idx,leaderArea);
                break;
            case LeaderPattern.DOTS:
                // if the width of a dot is larger than leader-pattern-width
                // ignore this property
                if (this.leaderPatternWidth < dotWidth) {
                    this.leaderPatternWidth = 0;
                }
                // if value of leader-pattern-width is 'use-font-metrics' (0)
                if (this.leaderPatternWidth == 0) {
                    if (dotWidth == 0) {
                        MessageHandler.errorln("char " + dot
                                               + " has width 0. Using width 100 instead.");
                        dotWidth = 100;
                    }
                    // if leader-alignment is used, calculate space to
                    // insert before leader so that all dots will be
                    // parallel.
                    if (leaderAlignment == LeaderAlignment.REFERENCE_AREA) {
                        int nextRepeatedLeaderPatternCycle
                            = (int)Math.ceil((double)position/(double)dotWidth);
                        int spaceBeforeLeader =
                            dotWidth * nextRepeatedLeaderPatternCycle
                            - position;
                        // appending indent space leader-alignment setting
                        // InlineSpace to false, so it is not used in line
                        // justification
                        System.out.println("align, space="+spaceBeforeLeader);
                        if (spaceBeforeLeader > 0) {
                            children.add(idx, new InlineSpace(spaceBeforeLeader,
                                                              false));
                            idx++;
                            // shorten leaderLength, otherwise - in
                            // case of leaderLength=remaining length -
                            // it will cut off the end of leaderlength
                            leaderLength -= spaceBeforeLeader;
                        }
                    }
                    int factor = (int)Math.floor(leaderLength / dotWidth);
                    char[] leaderChars = new char[factor];
                    for (int i = 0; i < factor; i++) {
                        leaderChars[i] = dot;
                    }
                    WordArea leaderPatternArea =
                        new WordArea(currentFontState,
                                     this.red, this.green, this.blue,
                                     new String(leaderChars), leaderLength);
                    leaderPatternArea.setYOffset(this.placementOffset);
                    children.add(idx, leaderPatternArea);
                } else {
                    // if leader-alignment is used, calculate space to
                    // insert before leader so that all dots will be
                    // parallel.
                    if (leaderAlignment == LeaderAlignment.REFERENCE_AREA) {
                        int nextRepeatedLeaderPatternCycle
                            = (int)Math.ceil((double)position/(double)leaderPatternWidth);
                        int spaceBeforeLeader =
                            leaderPatternWidth * nextRepeatedLeaderPatternCycle
                            - position;
                        // appending indent space leader-alignment setting
                        // InlineSpace to false, so it is not used in line
                        // justification
                        System.out.println("align, space="+spaceBeforeLeader);
                        if (spaceBeforeLeader > 0) {
                            children.add(idx, new InlineSpace(spaceBeforeLeader,
                                                              false));
                            idx++;
                            // shorten leaderLength, otherwise - in
                            // case of leaderLength=remaining length -
                            // it will cut off the end of leaderlength
                            leaderLength -= spaceBeforeLeader;
                        }
                    }
                    // calculate the space to insert between the dots
                    // and create a inline area with this width
                    int dotsFactor =
                        (int)Math.floor(((double)leaderLength)
                                        / ((double)this.leaderPatternWidth));
                    // add combination of dot + space to fill leader
                    // is there a way to do this in a more effective way?
                    for (int i = 0; i < dotsFactor; i++) {
                        InlineSpace spaceBetweenDots =
                            new InlineSpace(this.leaderPatternWidth - dotWidth, false);
                        WordArea leaderPatternArea =
                            new WordArea(this.fontState,
                                         this.red, this.green, this.blue,
                                         new String("."), dotWidth);
                        leaderPatternArea.setYOffset(this.placementOffset);
                        children.add(idx,leaderPatternArea);
                        idx++;
                        children.add(idx,spaceBetweenDots);
                        idx++;
                    }
                    // append at the end some space to fill up to leader length
                    children.add(idx,new InlineSpace(leaderLength
                                                     - dotsFactor
                                                     * this.leaderPatternWidth));
                    idx++;
                }
                break;
                // leader pattern use-content not implemented.
            case LeaderPattern.USECONTENT:
                MessageHandler.errorln("leader-pattern=\"use-content\" not "
                                       + "supported by this version of Fop");
                return;
            }
        }
    }
 
    public LineArea(FontState fontState, int lineHeight, int halfLeading,
                    int allocationWidth, int startIndent, int endIndent,
                    LineArea prevLineArea) {
        super(fontState);

        this.currentFontState = fontState;
        this.lineHeight = lineHeight;
        this.nominalFontSize = fontState.getFontSize();
        this.nominalGlyphHeight = fontState.getAscender()
            - fontState.getDescender();

        this.placementOffset = fontState.getAscender();
        this.contentRectangleWidth = allocationWidth - startIndent
            - endIndent;
        this.fontState = fontState;

        this.allocationHeight = this.nominalGlyphHeight;
        this.halfLeading = this.lineHeight - this.allocationHeight;

        this.startIndent = startIndent;
        this.endIndent = endIndent;

        if (prevLineArea != null) {
            // There might be InlineSpaces at the beginning
            // that should not be there - eat them
            boolean eatMoreSpace = true;
            pendingWidth = prevLineArea.pendingWidth;

            for (int i = 0; i < prevLineArea.pendingAreas.size(); i++) {
                Object b = prevLineArea.pendingAreas.get(i);
                if (eatMoreSpace) {
                    if (b instanceof InlineSpace) {
                        InlineSpace is = (InlineSpace)b;
                        if (is.isEatable()) {
                            pendingWidth -= is.getSize();
                        } else {
                            eatMoreSpace = false;
                            pendingAreas.add(b);
                        }
                    } else {
                        eatMoreSpace = false;
                        pendingAreas.add(b);
                    }
                } else {
                    pendingAreas.add(b);
                }
            }
            prevLineArea.pendingWidth=0;
            prevLineArea.pendingAreas=null;
        }
    }

    public void render(Renderer renderer) {
        renderer.renderLineArea(this);
    }

    public int addPageNumberCitation(String refid, LinkSet ls) {

        /*
         * We should add code here to handle the case where the page
         * number doesn't fit on the current line
         */

        // Space must be allocated for the page number, so currently we
        // give it 3 spaces

        int width = 3*getCharWidth(' ');


        PageNumberInlineArea pia
            = new PageNumberInlineArea(currentFontState,
                                       this.red, this.green, this.blue,
                                       refid, width);
        
        pia.setYOffset(placementOffset);
        pendingAreas.add(pia);
        pendingWidth += width;
        prev = TEXT;

        return -1;
    }


    /**
     * adds text to line area
     *
     * @return int character position
     */
    public int addText(char data[], int start, int end, LinkSet ls,
                       TextState textState) {
        // this prevents an array index out of bounds
        // which occurs when some text is laid out again.
        if (start == -1)
            return -1;
        boolean overrun = false;

        int wordStart = start;
        int wordLength = 0;
        int wordWidth = 0;
        // With CID fonts, space isn't neccesary currentFontState.width(32)
        int whitespaceWidth = getCharWidth(' ');

        boolean isText = false;
        boolean isMultiByteChar = false;

        /* iterate over each character */
        for (int i = start; i < end; i++) {
            int charWidth;
            /* get the character */
            char c = data[i];
            if (!(isSpace(c) || (c == '\n') || (c == '\r') || (c == '\t')
                  || (c == '\u2028'))) {
                charWidth = getCharWidth(c);
                isText = true;
                isMultiByteChar = (c > 127);
                // Add support for zero-width spaces
                if (charWidth <= 0 && c != '\u200B' && c != '\uFEFF')
                    charWidth = whitespaceWidth;
            } else {
                if ((c == '\n') || (c == '\r') || (c == '\t'))
                    charWidth = whitespaceWidth;
                else
                    charWidth = getCharWidth(c);

                isText = false;
                isMultiByteChar = false;

                if (prev == WHITESPACE) {

                    // if current & previous are WHITESPACE

                    if (this.whiteSpaceCollapse == WhiteSpaceCollapse.FALSE) {
                        if (isSpace(c)) {
                            spaceWidth += getCharWidth(c);
                        } else if (c == '\n' || c == '\u2028') {
                            // force line break
                            if (spaceWidth > 0) {
                                InlineSpace is = new InlineSpace(spaceWidth);
                                is.setUnderlined(textState.getUnderlined());
                                is.setOverlined(textState.getOverlined());
                                is.setLineThrough(textState.getLineThrough());
                                addChild(is);
                                finalWidth += spaceWidth;
                                spaceWidth = 0;
                            }
                            return i + 1;
                        } else if (c == '\t') {
                            spaceWidth += 8 * whitespaceWidth;
                        }
                    } else if (c == '\u2028') {
                        // Line separator
                        // Breaks line even if WhiteSpaceCollapse = True
                        if (spaceWidth > 0) {
                            InlineSpace is = new InlineSpace(spaceWidth);
                            is.setUnderlined(textState.getUnderlined());
                            is.setOverlined(textState.getOverlined());
                            is.setLineThrough(textState.getLineThrough());
                            addChild(is);
                            finalWidth += spaceWidth;
                            spaceWidth = 0;
                        }
                        return i + 1;
                    }

                } else if (prev == TEXT || prev == MULTIBYTECHAR ) {

                    // if current is WHITESPACE and previous TEXT
                    // the current word made it, so
                    // add the space before the current word (if there
                    // was some)

                    if (spaceWidth > 0) {
                        InlineSpace is = new InlineSpace(spaceWidth);
                        if (prevUlState) {
                            is.setUnderlined(textState.getUnderlined());
                        }
                        if (prevOlState) {
                            is.setOverlined(textState.getOverlined());
                        }
                        if (prevLTState) {
                            is.setLineThrough(textState.getLineThrough());
                        }
                        addChild(is);
                        finalWidth += spaceWidth;
                        spaceWidth = 0;
                    }

                    // add any pending areas

                    for (int j = 0; j < pendingAreas.size(); j++ ) {
                        Box box = (Box)pendingAreas.get(j);
                        if (box instanceof InlineArea) {
                            if (ls != null) {
                                Rectangle lr =
                                    new Rectangle(finalWidth, 0,
                                                  ((InlineArea)box).getContentWidth(),
                                                  fontState.getFontSize());
                                ls.addRect(lr, this, (InlineArea)box);
                            }
                        }
                        addChild(box);
                    }

                    finalWidth += pendingWidth;

                    // reset pending areas array
                    pendingWidth = 0;
                    pendingAreas = new ArrayList();

                    // add the current word

                    if (wordLength > 0) {
                        // The word might contain nonbreaking
                        // spaces. Split the word and add InlineSpace
                        // as necessary. All spaces inside the word
                        // Have a fixed width.
                        addSpacedWord(new String(data, wordStart, wordLength),
                                      ls, finalWidth, 0, textState, false);
                        finalWidth += wordWidth;

                        // reset word width
                        wordWidth = 0;
                    }

                    // deal with this new whitespace following the
                    // word we just added
                    prev = WHITESPACE;

                    embeddedLinkStart =
                        0;    // reset embeddedLinkStart since a space was encountered

                    spaceWidth = getCharWidth(c);

                    /*
                     * here is the place for white-space-treatment value 'ignore':
                     * if (this.spaceTreatment ==
                     * SpaceTreatment.IGNORE) {
                     * // do nothing
                     * } else {
                     * spaceWidth = currentFontState.width(32);
                     * }
                     */


                    if (this.whiteSpaceCollapse == WhiteSpaceCollapse.FALSE) {
                        if (c == '\n' || c == '\u2028') {
                            // force a line break
                            return i + 1;
                        } else if (c == '\t') {
                            spaceWidth = whitespaceWidth;
                        }
                    } else if (c == '\u2028') {
                        return i + 1;
                    }
                } else {

                    // if current is WHITESPACE and no previous

                    if (this.whiteSpaceCollapse == WhiteSpaceCollapse.FALSE) {
                        if (isSpace(c)) {
                            prev = WHITESPACE;
                            spaceWidth = getCharWidth(c);
                        } else if (c == '\n') {
                            // force line break
                            // textdecoration not used because spaceWidth is 0
                            InlineSpace is = new InlineSpace(spaceWidth);
                            addChild(is);
                            return i + 1;
                        } else if (c == '\t') {
                            prev = WHITESPACE;
                            spaceWidth = 8 * whitespaceWidth;
                        }

                    } else {
                        // skip over it
                        wordStart++;
                    }
                }

            }

            if (isText) {                        // current is TEXT

                int curr = isMultiByteChar ? MULTIBYTECHAR : TEXT;
                if (prev == WHITESPACE) {

                    // if current is TEXT and previous WHITESPACE

                    wordWidth = charWidth;
                    if ((finalWidth + spaceWidth + wordWidth)
                        > this.getContentWidth()) {
                        if (overrun)
                            MessageHandler.log("area contents overflows area");
                        if (this.wrapOption == WrapOption.WRAP) {
                            return i;
                        }
                    }
                    prev = curr;
                    wordStart = i;
                    wordLength = 1;
                } else if (prev == TEXT || prev == MULTIBYTECHAR ) {
                    if ( prev == TEXT && curr == TEXT || ! canBreakMidWord()) {
                        wordLength++;
    	                wordWidth += charWidth;
                    } else {

//                    if (spaceWidth > 0) { // for text-align="justify"
                        InlineSpace is = new InlineSpace(spaceWidth);
                        if (prevUlState) {
                            is.setUnderlined(textState.getUnderlined());
                        }
                        if (prevOlState) {
                            is.setOverlined(textState.getOverlined());
                        }
                        if (prevLTState) {
                            is.setLineThrough(textState.getLineThrough());
                        }
                        addChild(is);
                        finalWidth += spaceWidth;
                        spaceWidth = 0;
//                    }

                        // add any pending areas
                        
                        for (int j = 0; j < pendingAreas.size(); j++ ) {
                            Box box = (Box)pendingAreas.get(j);
                            if (box instanceof InlineArea) {
                                if (ls != null) {
                                    Rectangle lr =
                                        new Rectangle(finalWidth, 0,
                                                      ((InlineArea)box).getContentWidth(),
                                                      fontState.getFontSize());
                                    ls.addRect(lr, this, (InlineArea)box);
                                }
                            }
                            addChild(box);
                        }

                        finalWidth += pendingWidth;

                        // reset pending areas array
                        pendingWidth = 0;
                        pendingAreas = new ArrayList();

                        // add the current word

                        if (wordLength > 0) {
                            // The word might contain nonbreaking
                            // spaces. Split the word and add InlineSpace
                            // as necessary. All spaces inside the word
                            // have a fixed width.
                            addSpacedWord(new String(data, wordStart, wordLength),
                                          ls, finalWidth, 0, textState, false);
                            finalWidth += wordWidth;
                        }
                        spaceWidth = 0;
                        wordStart = i;
                        wordLength = 1;
                        wordWidth = charWidth;
                    }
                    prev = curr;
                } else {                         // nothing previous

                    prev = curr;
                    wordStart = i;
                    wordLength = 1;
                    wordWidth = charWidth;
                }

                if ((finalWidth + spaceWidth + pendingWidth + wordWidth)
                    > this.getContentWidth()) {

                    // BREAK MID WORD
/*                    if (canBreakMidWord()) {
                      addSpacedWord(new String(data, wordStart, wordLength - 1),
                      ls,
                      finalWidth + spaceWidth
                      + embeddedLinkStart, spaceWidth,
                      textState, false);
                      finalWidth += wordWidth;
                      wordWidth = 0;
                      return i;
                      }
*/
                    if (this.wrapOption == WrapOption.WRAP) {

                        if (hyphProps.hyphenate == Hyphenate.TRUE) {
                            int ret = wordStart;
                            ret = this.doHyphenation(data, i, wordStart,
                                                     this.getContentWidth()
                                                     - (finalWidth
                                                        + spaceWidth
                                                        + pendingWidth),
                                                     finalWidth + spaceWidth
                                                     + embeddedLinkStart,
                                                     ls, textState);

                            // current word couldn't be hypenated
                            // couldn't fit first word
                            // I am at the beginning of my line
                            if ((ret == wordStart) &&
                                (wordStart == start) &&
                                (finalWidth == 0)) {

                                MessageHandler.log("area contents overflows area");
                                addSpacedWord(new String(data, wordStart, wordLength - 1),
                                              ls,
                                              embeddedLinkStart,
                                              spaceWidth, textState, false);

                                finalWidth += wordWidth;
                                wordWidth = 0;
                                ret = i;
                            }
                            return ret;
                        } else if (wordStart == start) {
                            // first word
                            overrun = true;
                            // if not at start of line, return word start
                            // to try again on a new line
                            if (finalWidth > 0) {
                                return wordStart;
                            }
                        } else {
                            return wordStart;
                        }

                    }
                }
            }
        } // end of iteration over text

        if (prev == TEXT || prev == MULTIBYTECHAR) {

            if (spaceWidth > 0) {
                InlineSpace pis = new InlineSpace(spaceWidth);
                // Make sure that this space doesn't occur as
                // first thing in the next line
                pis.setEatable(true);
                if (prevUlState) {
                    pis.setUnderlined(textState.getUnderlined());
                }
                if (prevOlState) {
                    pis.setOverlined(textState.getOverlined());
                }
                if (prevLTState) {
                    pis.setLineThrough(textState.getLineThrough());
                }
                pendingAreas.add(pis);
                pendingWidth += spaceWidth;
                spaceWidth = 0;
            }

            addSpacedWord(new String(data, wordStart, wordLength), ls,
                          finalWidth + pendingWidth,
                          spaceWidth, textState, true);

            embeddedLinkStart += wordWidth;
            wordWidth = 0;
        }

        if (overrun)
            MessageHandler.log("area contents overflows area");
        return -1;
    }

    /**
     * adds a Leader; actually the method receives the leader properties
     * and creates a leader area or an inline area which is appended to
     * the children of the containing line area. <br>
     * leader pattern use-content is not implemented.
     */
    public void addLeader(int leaderPattern, int leaderLengthMinimum,
                          int leaderLengthOptimum, int leaderLengthMaximum,
                          int ruleStyle, int ruleThickness,
                          int leaderPatternWidth, int leaderAlignment) {
        System.out.println(
            "min="+leaderLengthMinimum+
            " opt="+leaderLengthOptimum+
            " max="+leaderLengthMaximum+
            " th="+ruleThickness+
            " al="+leaderAlignment);
        if (leaderLengthMinimum>leaderLengthOptimum
            || leaderLengthOptimum>leaderLengthMaximum) {
            MessageHandler.errorln("leader sizes wrong");
            return;
        }
        if (leaderLengthOptimum>getRemainingWidth()) {
            MessageHandler.errorln("leader width assertion failed");
            return;
        }
        addPending();
        children.add(new LineArea.Leader(leaderPattern, leaderLengthMinimum,
                                         leaderLengthOptimum, leaderLengthMaximum,
                                         ruleStyle, ruleThickness,
                                         leaderPatternWidth, leaderAlignment,
                                         fontState, red, green, blue,
                                         placementOffset, getCurrentXPosition()));
        finalWidth += leaderLengthOptimum;
    }

    /**
     * adds pending inline areas to the line area
     * normally done, when the line area is filled and
     * added as child to the parent block area
     */
    public void addPending() {
        if (spaceWidth > 0) {
            addChild(new InlineSpace(spaceWidth));
            finalWidth += spaceWidth;
            spaceWidth = 0;
        }

        for (int i = 0; i < pendingAreas.size(); i++ ) {
            Box box = (Box)pendingAreas.get(i);
            addChild(box);
        }

        finalWidth += pendingWidth;

        // reset pending areas array
        pendingWidth = 0;
        pendingAreas = new ArrayList();
    }

    /**
     * aligns line area
     *
     */
    public void align(int type) {
        int padding = 0;

        switch (type) {
        case TextAlign.START:      // left
            padding = this.getContentWidth() - finalWidth;
            endIndent += padding;
            for (int i = 0; i < children.size(); i++) {
                Object o = children.get(i);
                if (o instanceof LineArea.Leader) {
                    LineArea.Leader leader = (LineArea.Leader)o;
                    leader.expand(leader.leaderLengthOptimum);
                }
            }
            break;
        case TextAlign.END:        // right
            padding = this.getContentWidth() - finalWidth;
            startIndent += padding;
            for (int i = 0; i < children.size(); i++) {
                Object o = children.get(i);
                if (o instanceof LineArea.Leader) {
                    LineArea.Leader leader = (LineArea.Leader)o;
                    leader.expand(leader.leaderLengthOptimum);
                }
            }
            break;
        case TextAlign.CENTER:     // center
            padding = (this.getContentWidth() - finalWidth) / 2;
            startIndent += padding;
            endIndent += padding;
            for (int i = 0; i < children.size(); i++) {
                Object o = children.get(i);
                if (o instanceof LineArea.Leader) {
                    LineArea.Leader leader = (LineArea.Leader)o;
                    leader.expand(leader.leaderLengthOptimum);
                }
            }
            break;
        case TextAlign.JUSTIFY:    // justify
            // first pass - count the spaces
            int leaderCount = 0;
            int spaceCount = 0;
            for (int i = 0; i < children.size(); i++ ) {
                Object o = children.get(i);
                if (o instanceof InlineSpace) {
                    InlineSpace space = (InlineSpace)o;
                    if (space.getResizeable()) {
                        spaceCount++;
                    }
                } else if(o instanceof LineArea.Leader) {
                    leaderCount++;
                }
            }
            padding = (this.getContentWidth() - finalWidth);
            if (padding>0) {
                if (leaderCount>0) {
                    int offset=0;
                    for (int i = 0; i < children.size(); i++) {
                        Object o = children.get(i);
                        if (o instanceof LineArea.Leader) {
                            LineArea.Leader leader = (LineArea.Leader)o;
                            int leaderExpansionMaximum=
                                leader.leaderLengthMaximum - leader.leaderLengthOptimum;
                            if (leaderExpansionMaximum < padding) {
                                leader.expand(leader.leaderLengthMaximum);
                                padding-=leaderExpansionMaximum;
                                offset+=leaderExpansionMaximum;
                            } else {
                                leader.expand(leader.leaderLengthOptimum + padding);
                                padding=0;
                                offset+=padding;
                            }
                        } else if (o instanceof InlineArea) {
                            ((InlineArea)o).setXOffset(((InlineArea)o).getXOffset() + offset);
                        }
                    }
                }
                if (spaceCount > 0 && padding > 0) {
                    padding = padding/spaceCount;
                } else {               // no spaces
                    padding = 0;
                }
                spaceCount = 0;
                // second pass - add additional space
                for (int i = 0; i < children.size(); i++) {
                    Object o = children.get(i);
                    if (o instanceof InlineSpace) {
                        InlineSpace space = (InlineSpace)o;
                        if (space.getResizeable()) {
                            space.setSize(space.getSize() + padding);
                            spaceCount++;
                        }
                    } else if (o instanceof InlineArea) {
                        ((InlineArea)o).setXOffset(((InlineArea)o).getXOffset() + i * padding);
                    }
                }
            }
        default: 
            MessageHandler.errorln("bad align: "+type);
        }
    }

    /**
     * Balance (vertically) the inline areas within this line.
     */
    public void verticalAlign() {
        int superHeight = -this.placementOffset;
        int maxHeight = this.allocationHeight;
        for (int i = 0; i < children.size(); i++ ) {
            Box b = (Box)children.get(i);
            if (b instanceof InlineArea) {
                InlineArea ia = (InlineArea)b;
                if (ia instanceof WordArea) {
                    ia.setYOffset(placementOffset);
                }
                if (ia.getHeight() > maxHeight) {
                    maxHeight = ia.getHeight();
                }
                int vert = ia.getVerticalAlign();
                if (vert == VerticalAlign.SUPER) {
                    int fh = fontState.getAscender();
                    ia.setYOffset((int)(placementOffset - (2 * fh / 3.0)));
                } else if (vert == VerticalAlign.SUB) {
                    int fh = fontState.getAscender();
                    ia.setYOffset((int)(placementOffset + (2 * fh / 3.0)));
                }
            } else {}
        }
        // adjust the height of this line to the
        // resulting alignment height.
        this.allocationHeight = maxHeight;
    }

    public void changeColor(float red, float green, float blue) {
        this.red = red;
        this.green = green;
        this.blue = blue;
    }

    public void changeFont(FontState fontState) {
        this.currentFontState = fontState;
    }

    public void changeWhiteSpaceCollapse(int whiteSpaceCollapse) {
        this.whiteSpaceCollapse = whiteSpaceCollapse;
    }

    public void changeWrapOption(int wrapOption) {
        this.wrapOption = wrapOption;
    }

    public void changeVerticalAlign(int vAlign) {
        this.vAlign = vAlign;
    }

    public int getEndIndent() {
        return endIndent;
    }

    public int getHeight() {
        return this.allocationHeight;
    }

    public int getPlacementOffset() {
        return this.placementOffset;
    }

    public int getStartIndent() {
        return startIndent;
    }

    public boolean isEmpty() {
        return !(pendingAreas.size() > 0 || children.size() > 0);
        // return (prev == NOTHING);
    }

    /**
     * sets hyphenation related traits: language, country, hyphenate, hyphenation-character
     * and minimum number of character to remain one the previous line and to be on the
     * next line.
     */
    public void changeHyphenation(HyphenationProps hyphProps) {
        this.hyphProps = hyphProps;
    }


    /**
     * creates a leader as String out of the given char and the leader length
     * and wraps it in an InlineArea which is returned
     */
    private InlineArea buildSimpleLeader(char c, int leaderLength) {
        int width = getCharWidth(c);
        if (width == 0) {
            MessageHandler.errorln("char " + c
                                   + " has width 0. Using width 100 instead.");
            width = 100;
        }
        int factor = (int)Math.floor(leaderLength / width);
        char[] leaderChars = new char[factor];
        for (int i = 0; i < factor; i++) {
            leaderChars[i] = c;
        }
        WordArea leaderPatternArea = new WordArea(currentFontState, this.red,
                                                  this.green, this.blue,
                                                  new String(leaderChars),
                                                  leaderLength);
        leaderPatternArea.setYOffset(placementOffset);
        return leaderPatternArea;
    }

    /**
     * calculates the used space in this line area
     */
    private int getCurrentXPosition() {
        return finalWidth + spaceWidth + startIndent + pendingWidth;
    }

    /**
     * extracts a complete word from the character data
     */
    private String getHyphenationWord(char[] characters, int wordStart) {
        boolean wordendFound = false;
        int counter = 0;
        char[] newWord = new char[characters.length];    // create a buffer
        while ((!wordendFound)
               && ((wordStart + counter) < characters.length)) {
            char tk = characters[wordStart + counter];
            if (Character.isLetter(tk)) {
                newWord[counter] = tk;
                counter++;
            } else {
                wordendFound = true;
            }
        }
        return new String(newWord, 0, counter);
    }


    /**
     * extracts word for hyphenation and calls hyphenation package,
     * handles cases of inword punctuation and quotation marks at the beginning
     * of words, but not in a internationalized way
     */
    public int doHyphenation(char[] characters, int position, int wordStart,
                             int remainingWidth, int startw, LinkSet ls,
                             TextState textState) {
        // check whether the language property has been set
        if (this.hyphProps.language.equalsIgnoreCase("none")) {
            MessageHandler.errorln("if property 'hyphenate' is used, a language must be specified");
            return wordStart;
        }

        /**
         * remaining part string of hyphenation
         */
        StringBuffer remainingString = new StringBuffer();

        /**
         * for words with some inword punctuation like / or -
         */
        StringBuffer preString = null;

        /**
         * in word punctuation character
         */
        char inwordPunctuation;

        /**
         * the complete word handed to the hyphenator
         */
        String wordToHyphenate;

        // width of hyphenation character
        int hyphCharWidth = getCharWidth(this.hyphProps.hyphenationChar);
        remainingWidth -= hyphCharWidth;

        // handles ' or " at the beginning of the word
        if (characters[wordStart] == '"' || characters[wordStart] == '\'') {
            remainingString.append(characters[wordStart]);
            // extracts whole word from string
            wordToHyphenate = getHyphenationWord(characters, wordStart + 1);
        } else {
            wordToHyphenate = getHyphenationWord(characters, wordStart);
        }

        // if the extracted word is smaller than the remaining width
        // we have a non letter character inside the word. at the moment
        // we will only handle hard hyphens and slashes
        if (getWordWidth(wordToHyphenate) < remainingWidth) {
            inwordPunctuation =
                characters[wordStart + remainingString.length()
                           + wordToHyphenate.length()];
            if (inwordPunctuation == '-' || inwordPunctuation == '/') {
                preString = new StringBuffer(wordToHyphenate);
                preString = preString.append(inwordPunctuation);
                wordToHyphenate =
                    getHyphenationWord(characters,
                                       wordStart + remainingString.length()
                                       + wordToHyphenate.length() + 1);
                remainingWidth -=
                    (getWordWidth(wordToHyphenate)
                     + getCharWidth(inwordPunctuation));
            }
        }

        // are there any hyphenation points
        Hyphenation hyph =
            Hyphenator.hyphenate(hyphProps.language, hyphProps.country,
                                 wordToHyphenate,
                                 hyphProps.hyphenationRemainCharacterCount,
                                 hyphProps.hyphenationPushCharacterCount);
        if (hyph == null && preString == null) {
            // no hyphenation points and no inword non letter character
            return wordStart;
        } else if (hyph == null && preString != null) {
            // no hyphenation points, but a inword non-letter character
            remainingString.append(preString);
            this.addWord(remainingString, startw, ls, textState);
            return wordStart + remainingString.length();
        } else if (hyph != null && preString == null) {
            // hyphenation points and no inword non-letter character
            int index = getFinalHyphenationPoint(hyph, remainingWidth);
            if (index != -1) {
                remainingString.append(hyph.getPreHyphenText(index));
                remainingString.append(this.hyphProps.hyphenationChar);
                this.addWord(remainingString, startw, ls, textState);
                return wordStart + remainingString.length() - 1;
            }
        } else if (hyph != null && preString != null) {
            // hyphenation points and a inword non letter character
            int index = getFinalHyphenationPoint(hyph, remainingWidth);
            if (index != -1) {
                remainingString.append(preString.append(hyph.getPreHyphenText(index)));
                remainingString.append(this.hyphProps.hyphenationChar);
                this.addWord(remainingString, startw, ls, textState);
                return wordStart + remainingString.length() - 1;
            } else {
                remainingString.append(preString);
                this.addWord(remainingString, startw, ls, textState);
                return wordStart + remainingString.length();
            }
        }
        return wordStart;
    }


    /**
     * Calculates the wordWidth using the actual fontstate
     */
    private int getWordWidth(String word) {
        if (word == null)
            return 0;
        int wordLength = word.length();
        int width = 0;
        char[] characters = new char[wordLength];
        word.getChars(0, wordLength, characters, 0);

        for (int i = 0; i < wordLength; i++) {
            width += getCharWidth(characters[i]);
        }
        return width;
    }

    public int getRemainingWidth() {
        return this.getContentWidth() + startIndent - this.getCurrentXPosition();
    }

    public void setLinkSet(LinkSet ls) {}

    public void addInlineArea(InlineArea box, LinkSet ls) {
        addPending();
        addChild(box);
	if (ls != null) {
	    Rectangle lr=new Rectangle(finalWidth, 0,box.getContentWidth(),
				       box.getContentHeight());
	    ls.addRect(lr, this, box);
	}
        prev = TEXT;
        finalWidth += box.getContentWidth();
    }

    public void addInlineSpace(InlineSpace is, int spaceWidth) {
        addChild(is);
        finalWidth += spaceWidth;
        // spaceWidth = 0;
    }

    /**
     * adds a single character to the line area tree
     */
    public int addCharacter(char data, LinkSet ls, boolean ul) {
        WordArea ia = null;
        int remainingWidth = this.getRemainingWidth();
        int width = getCharWidth(data);
        // if it doesn't fit, return
        if (width > remainingWidth) {
            return org.apache.fop.fo.flow.Character.DOESNOT_FIT;
        } else {
            // if whitespace-collapse == true, discard character
            if (Character.isSpaceChar(data)
                && whiteSpaceCollapse == WhiteSpaceCollapse.TRUE) {
                return org.apache.fop.fo.flow.Character.OK;
            }
            // create new InlineArea
            ia = new WordArea(currentFontState, this.red, this.green,
                              this.blue, new Character(data).toString(),
                              width);
            ia.setYOffset(placementOffset);
            ia.setUnderlined(ul);
            pendingAreas.add(ia);
            if (Character.isSpaceChar(data)) {
                this.spaceWidth = +width;
                prev = LineArea.WHITESPACE;
            } else {
                pendingWidth += width;
                prev = LineArea.TEXT;
            }
            return org.apache.fop.fo.flow.Character.OK;
        }
    }



    /**
     * adds a InlineArea containing the String wordBuf to
     * the line area children.
     */
    private void addWord(StringBuffer wordBuf, int startw,
                         LinkSet ls, TextState textState) {  
        if (spaceWidth > 0) {
            InlineSpace is = new InlineSpace(spaceWidth);
            if (prevUlState) {
                is.setUnderlined(textState.getUnderlined());
            }
            if (prevOlState) {
                is.setOverlined(textState.getOverlined());
            }
            if (prevLTState) {
                is.setLineThrough(textState.getLineThrough());
            }
            addChild(is);
            finalWidth += spaceWidth;
            spaceWidth = 0;
        }

        // add any pending areas

        for (int i = 0; i < pendingAreas.size(); i++ ) {
            Box box = (Box)pendingAreas.get(i);
            if (box instanceof InlineArea) {
                if (ls != null) {
                    Rectangle lr =
                        new Rectangle(finalWidth, 0,
                                      ((InlineArea)box).getContentWidth(),
                                      fontState.getFontSize());
                    ls.addRect(lr, this, (InlineArea)box);
                }
            }
            addChild(box);
        }

        finalWidth += pendingWidth;

        // reset pending areas array
        pendingWidth = 0;
        pendingAreas = new ArrayList();
        String word = (wordBuf != null) ? wordBuf.toString() : "";
        int wordWidth = this.getWordWidth(word);
        WordArea hia = new WordArea(currentFontState,
                                    this.red, this.green, this.blue,
                                    word, wordWidth);
        hia.setYOffset(placementOffset);
        hia.setUnderlined(textState.getUnderlined());
        prevUlState = textState.getUnderlined();
        hia.setOverlined(textState.getOverlined());
        prevOlState = textState.getOverlined();
        hia.setLineThrough(textState.getLineThrough());
        prevLTState = textState.getLineThrough();
        hia.setVerticalAlign(vAlign);
        this.addChild(hia);

        if (ls != null) {
            Rectangle lr = new Rectangle(startw, 0,
                                         hia.getContentWidth(),
                                         fontState.getFontSize());
            ls.addRect(lr, this, hia);
        }
        // calculate the space needed
        finalWidth +=  wordWidth;
    }


    /**
     * extracts from a hyphenated word the best (most greedy) fit
     */
    private int getFinalHyphenationPoint(Hyphenation hyph,
                                         int remainingWidth) {
        int[] hyphenationPoints = hyph.getHyphenationPoints();
        int numberOfHyphenationPoints = hyphenationPoints.length;

        int index = -1;
        String wordBegin = "";
        int wordBeginWidth = 0;

        for (int i = 0; i < numberOfHyphenationPoints; i++) {
            wordBegin = hyph.getPreHyphenText(i);
            if (this.getWordWidth(wordBegin) > remainingWidth) {
                break;
            }
            index = i;
        }
        return index;
    }

    /**
     * Checks if it's legal to break a word in the middle
     * based on the current language property.
     * @return true if legal to break word in the middle
     */
    private boolean canBreakMidWord() {
        boolean ret = false;
        if (hyphProps != null && hyphProps.language != null
            &&!hyphProps.language.equals("NONE")) {
            String lang = hyphProps.language.toLowerCase();
            if ("zh".equals(lang) || "ja".equals(lang) || "ko".equals(lang)
                || "vi".equals(lang))
                ret = true;
        }
        return ret;
    }

    private final int getEmWidth() {
        char mappedChar = currentFontState.mapChar('m');
        // the mapping returns '#' for unmapped characters in standard fonts
        // what happens for other fonts?
        if (mappedChar == '#') {
            return 500 * currentFontState.getFontSize();
        } else {
            return currentFontState.width(mappedChar);
        }
    }

    private final int getEnWidth() {
        char mappedChar = currentFontState.mapChar('n');
        // the mapping returns '#' for unmapped characters in standard fonts
        // what happens for other fonts?
        if (mappedChar != '#') {
            // should do something to discover non-proportional fonts
            return (getEmWidth()*9)/10;
        } else {
            return currentFontState.width(mappedChar);
        }
    }

    /**
     * Helper method for getting the width of a unicode char
     * from the current fontstate.
     * This also performs some guessing on widths on various
     * versions of space that might not exists in the font.
     */
    private int getCharWidth(char c) {
        if ((c == '\n') || (c == '\r') || (c == '\t')) {
            return getCharWidth(' ');
        } else {
            char mappedChar = currentFontState.mapChar(c);
            if (mappedChar == '#' || mappedChar == 0) {
                // Estimate the width of spaces not represented in
                // the font
                if (c == '#') {
                    return currentFontState.width(mappedChar);
                } else if (c == ' ') {
                    return getEmWidth();
                } else if (c == '\u00A0') {
                    return getCharWidth(' ');
                } else if (c == '\u2000') {
                    return getEnWidth();
                } else if (c == '\u2001') {
                    return getEmWidth();
                } else if (c == '\u2002') {
                    return getEnWidth();
                } else if (c == '\u2003') {
                    return getEmWidth();
                } else if (c == '\u2004') {
                    return getEmWidth() / 3;
                } else if (c == '\u2005') {
                    return getEmWidth() / 4;
                } else if (c == '\u2006') {
                    return getEmWidth() / 6;
                } else if (c == '\u2007') {
                    return getCharWidth(' ');
                } else if (c == '\u2008') {
                    return getCharWidth('.');
                } else if (c == '\u2009') {
                    return getEmWidth() / 5;
                } else if (c == '\u200A') {
                    return getEmWidth() / 10;
                } else if (c == '\u200B') {
                    return 1;
                } else if (c == '\u202F') {
                    return getCharWidth(' ') / 2;
                } else if (c == '\u3000') {
                    return getCharWidth(' ') * 2;
                } else if (c == '\u3000') {
                    return getCharWidth(' ') * 2;
                } else {
                    return currentFontState.width(mappedChar);
                }
            } else {
                return currentFontState.width(mappedChar);
            }
        }
    }


    /**
     * Helper method to determine if the character is a
     * space with normal behaviour. Normal behaviour means that
     * it's not non-breaking
     */
    private boolean isSpace(char c) {
        if (c == ' ' || c == '\u2000' ||    // en quad
            c == '\u2001' ||                    // em quad
            c == '\u2002' ||                    // en space
            c == '\u2003' ||                    // em space
            c == '\u2004' ||                    // three-per-em space
            c == '\u2005' ||                    // four--per-em space
            c == '\u2006' ||                    // six-per-em space
            c == '\u2007' ||                    // figure space
            c == '\u2008' ||                    // punctuation space
            c == '\u2009' ||                    // thin space
            c == '\u200A' ||                    // hair space
            c == '\u200B')                      // zero width space
            return true;
        else
            return false;
    }


    /**
     * Method to determine if the character is a nonbreaking
     * space.
     */
    private boolean isNBSP(char c) {
        if (c == '\u00A0' || c == '\u202F' ||    // narrow no-break space
            c == '\u3000' ||                    // ideographic space
            c == '\uFEFF') {                    // zero width no-break space
            return true;
        } else
            return false;
    }

    /**
     * @return true if the character represents any kind of space
     */
    private boolean isAnySpace(char c) {
        boolean ret = (isSpace(c) || isNBSP(c));
        return ret;
    }

    /**
     * Add a word that might contain non-breaking spaces.
     * Split the word into WordArea and InlineSpace and add it.
     * If addToPending is true, add to pending areas.
     */
    private void addSpacedWord(String word, LinkSet ls, int startw,
                               int spacew, TextState textState,
                               boolean addToPending) {
        StringTokenizer st = new StringTokenizer(word, "\u00A0\u202F\u3000\uFEFF", true);
        while (st.hasMoreTokens()) {
            String currentWord = st.nextToken();

            if (currentWord.length() == 1
                && (isNBSP(currentWord.charAt(0)))) {
                // Add an InlineSpace
                int spaceWidth = getCharWidth(currentWord.charAt(0));
                if (spaceWidth > 0) {
                    InlineSpace is = new InlineSpace(spaceWidth);
                    startw += spaceWidth;
                    if (prevUlState) {
                        is.setUnderlined(textState.getUnderlined());
                    }
                    if (prevOlState) {
                        is.setOverlined(textState.getOverlined());
                    }
                    if (prevLTState) {
                        is.setLineThrough(textState.getLineThrough());
                    }

                    if (addToPending) {
                        pendingAreas.add(is);
                        pendingWidth += spaceWidth;
                    } else {
                        addChild(is);
                    }
                }
            } else {
                int wordWidth = getWordWidth(currentWord);
                WordArea ia = new WordArea(currentFontState, this.red,
                                           this.green, this.blue,
                                           currentWord,
                                           wordWidth);
                ia.setYOffset(placementOffset);
                ia.setUnderlined(textState.getUnderlined());
                prevUlState = textState.getUnderlined();
                ia.setOverlined(textState.getOverlined());
                prevOlState = textState.getOverlined();
                ia.setLineThrough(textState.getLineThrough());
                prevLTState = textState.getLineThrough();
                ia.setVerticalAlign(vAlign);

                if (addToPending) {
                    pendingAreas.add(ia);
                    pendingWidth += wordWidth;
                } else {
                    addChild(ia);
                }
                if (ls != null) {
                    Rectangle lr = new Rectangle(startw, spacew,
                                                 ia.getContentWidth(),
                                                 fontState.getFontSize());
                    ls.addRect(lr, this, ia);
                }
                startw += wordWidth;
            }
        }
    }

}

