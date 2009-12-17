/*
 * Copyright 2009 Mike Cumings
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kenai.jbosh;

import java.util.logging.Logger;
import org.junit.Test;
import static org.junit.Assert.*;

public class ServiceLibTest {

    private static final Logger LOG =
            Logger.getLogger(ServiceLibTest.class.getName());

    private static final class LinkageErrorImpl {
        public LinkageErrorImpl() {
            throw(new LinkageError("Simulated linkage error"));
        }
    }

    /*
     * JBOSH-17: Ensure that LinkageErrors are handled during service load.
     */
    @Test
    public void linkageErrorIsCaught() throws Exception {
        try {
            System.setProperty(LinkageErrorImpl.class.getName(),
                    LinkageErrorImpl.class.getName());
            ServiceLib.loadService(LinkageErrorImpl.class);
            fail("Should not make it here");
        } catch (IllegalStateException isx) {
            // Couldn't find a service impl.  This is good.
        } catch (Throwable thr) {
            fail("LinkageError exposed a throwable");
        } finally {
            System.getProperties().remove(LinkageErrorImpl.class.getName());
        }
    }

}
