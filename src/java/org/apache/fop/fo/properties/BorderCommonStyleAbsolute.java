/*
 *
 * Copyright 2004 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Created on 20/04/2004
 * $Id$
 */
package org.apache.fop.fo.properties;

import org.apache.fop.fo.FONode;
import org.apache.fop.fo.PropNames;
import org.apache.fop.fo.expr.PropertyException;

/**
 * Base class for border-&lt;absolute&gt;-style properties, providing the
 * methods necessary to resolve corresponding relative properties.
 * 
 * @author pbw
 * @version $Revision$ $Name$
 */
public abstract class BorderCommonStyleAbsolute
extends BorderCommonStyle {

    /** Array of relative border style properties,
     * indexed by relative edge constants */
    private static int[] relBorderStyleProps = {
            PropNames.NO_PROPERTY
            ,PropNames.BORDER_BEFORE_STYLE
            ,PropNames.BORDER_AFTER_STYLE
            ,PropNames.BORDER_START_STYLE
            ,PropNames.BORDER_END_STYLE
    };

    /**
     * Gets the relative border style property corresponding to the given
     * absolute edge
     * @param foNode the node on which the property is being defined
     * @param absoluteEdge
     * @return the relative border style property index
     * @throws PropertyException
     */
    protected int getCorrespondingStyleProperty(
            FONode foNode, int absoluteEdge)
    throws PropertyException {
        int relEdge = WritingMode.getCorrespondingRelativeEdge(
                foNode.getWritingMode(), absoluteEdge);
        return relBorderStyleProps[relEdge];
    }

    public boolean isCorrespondingAbsolute() {
        return true;
    }
}
