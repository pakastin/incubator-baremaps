/*
 * Copyright (C) 2020 The Baremaps Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.baremaps.store;

import com.baremaps.store.memory.Memory;
import com.baremaps.store.type.IntegerDataType;

/** A list of integers backed by a {@link Memory}. */
public class LongDataList extends AlignedDataList<Integer> {

  /**
   * Constructs a list.
   *
   * @param memory the memory
   */
  public LongDataList(Memory memory) {
    super(new IntegerDataType(), memory);
  }
}