/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"), you may not use this file except in compliance with
 * the License.
 * You may obtain a copy of the License at
 * https://github.com/etendosoftware/etendo_core/blob/main/legal/Etendo_license.txt
 * Software distributed under the License is distributed on an
 * "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing rights
 * and limitations under the License.
 * All portions are Copyright © 2021–2025 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */

package com.etendoerp.metadata.data;

public class ButtonConfig {
    public final String id;
    public final String name;
    public final String action;
    public final boolean enabled;
    public final String icon;

    public ButtonConfig(String id, String name, String action, boolean enabled, String icon) {
        this.id = id;
        this.name = name;
        this.action = action;
        this.enabled = enabled;
        this.icon = icon;
    }
}