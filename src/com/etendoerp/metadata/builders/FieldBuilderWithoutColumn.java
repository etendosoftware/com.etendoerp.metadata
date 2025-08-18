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

package com.etendoerp.metadata.builders;

import org.openbravo.model.ad.access.FieldAccess;
import org.openbravo.model.ad.ui.Field;

/**
 * Concrete implementation of FieldBuilder for fields without columns
 * @author Futit Services S.L.
 */
public class FieldBuilderWithoutColumn extends FieldBuilder {

    public FieldBuilderWithoutColumn(Field field, FieldAccess fieldAccess) {
        super(field, fieldAccess);
    }

}
