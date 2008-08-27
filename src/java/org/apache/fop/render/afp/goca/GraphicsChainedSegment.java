/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* $Id: $ */

package org.apache.fop.render.afp.goca;

import java.io.IOException;
import java.io.OutputStream;

import org.apache.fop.render.afp.modca.AbstractPreparedObjectContainer;
import org.apache.fop.render.afp.modca.PreparedAFPObject;
import org.apache.fop.render.afp.tools.BinaryUtils;

/**
 * A GOCA graphics segment
 */
public final class GraphicsChainedSegment extends AbstractPreparedObjectContainer {

    /** The maximum segment data length */
    protected static final int MAX_DATA_LEN = 8192;

    /** the current area */
    private GraphicsArea currentArea = null;

    /** the previous segment in the chain */
    private GraphicsChainedSegment previous = null;

    /** the next segment in the chain */
    private GraphicsChainedSegment next = null;

    /**
     * Main constructor
     *
     * @param name
     *            the name of this graphics segment
     */
    public GraphicsChainedSegment(String name) {
        super(name);
    }

    /**
     * Constructor
     *
     * @param name
     *            the name of this graphics segment
     * @param previous
     *            the previous graphics segment in this chain
     */
    public GraphicsChainedSegment(String name, GraphicsChainedSegment previous) {
        super(name);
        previous.next = this;
        this.previous = previous;
    }

    /** {@inheritDoc} */
    public int getDataLength() {
        int dataLen = 14 + super.getDataLength();
        if (previous == null) {
            GraphicsChainedSegment current = this.next;
            while (current != null) {
                dataLen += current.getDataLength();
                current = current.next;
            }
        }
        return dataLen;
    }

    private static final byte APPEND_NEW_SEGMENT = 0;
//    private static final byte PROLOG = 4;
//    private static final byte APPEND_TO_EXISING = 48;

    private static final int NAME_LENGTH = 4;

    /** {@inheritDoc} */
    protected int getNameLength() {
        return NAME_LENGTH;
    }

    /** {@inheritDoc} */
    protected void writeStart(OutputStream os) throws IOException {
        super.writeStart(os);
        int len = super.getDataLength();
        byte[] segLen = BinaryUtils.convert(len, 2);

        byte[] nameBytes = getNameBytes();
        byte[] data = new byte[] {
            0x70, // BEGIN_SEGMENT
            0x0C, // Length of following parameters
            nameBytes[0],
            nameBytes[1],
            nameBytes[2],
            nameBytes[3],
            0x00, // FLAG1 (ignored)
            APPEND_NEW_SEGMENT,
            segLen[0], // SEGL
            segLen[1],
            0x00,
            0x00,
            0x00,
            0x00
        };
        // P/S NAME (predecessor name)
        if (previous != null) {
            nameBytes = previous.getNameBytes();
            System.arraycopy(nameBytes, 0, data, 10, NAME_LENGTH);
        }
        os.write(data);
    }

    /** {@inheritDoc} */
    protected void writeEnd(OutputStream os) throws IOException {
        // I am the first segment in the chain so write out the rest
        if (previous == null) {
            GraphicsChainedSegment current = this.next;
            while (current != null) {
                current.writeToStream(os);
                current = current.next;
            }
        }
    }

    /** Begins a graphics area (start of fill) */
    protected void beginArea() {
        this.currentArea = new GraphicsArea();
        super.addObject(currentArea);
    }

    /** Ends a graphics area (end of fill) */
    protected void endArea() {
        this.currentArea = null;
    }

    /** {@inheritDoc} */
    public PreparedAFPObject addObject(PreparedAFPObject drawingOrder) {
        if (currentArea != null) {
            currentArea.addObject(drawingOrder);
        } else {
            super.addObject(drawingOrder);
        }
        return drawingOrder;
    }

    /** {@inheritDoc} */
    public String toString() {
        return "GraphicsChainedSegment(name=" + super.getName() + ")";
    }
}