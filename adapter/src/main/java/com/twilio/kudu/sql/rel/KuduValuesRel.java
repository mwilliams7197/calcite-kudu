/* Copyright 2020 Twilio, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.twilio.kudu.sql.rel;

import com.google.common.collect.ImmutableList;
import com.twilio.kudu.sql.KuduRelNode;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.core.Values;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.rex.RexLiteral;

import java.util.stream.Collectors;

public class KuduValuesRel extends Values implements KuduRelNode {

  public KuduValuesRel(RelOptCluster cluster, RelDataType rowType, ImmutableList<ImmutableList<RexLiteral>> tuples,
      RelTraitSet traits) {
    super(cluster, rowType, tuples, traits);
  }

  @Override
  public void implement(Implementor implementor) {
    implementor.columnIndexes = rowType.getFieldList().stream().map(RelDataTypeField::getIndex)
        .collect(Collectors.toList());
    implementor.tuples = tuples;
  }

}
