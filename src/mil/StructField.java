/*
    Copyright 2018 Mark P Jones, Portland State University

    This file is part of mil-tools.

    mil-tools is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    mil-tools is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with mil-tools.  If not, see <https://www.gnu.org/licenses/>.
*/
package mil;

import compiler.*;
import compiler.Position;
import core.*;

/** Represents a single field of a struct type. */
public class StructField extends Name {

  /** Specifies the type of area in this field. */
  private Type type;

  /** The offset (measured in bytes from the start of the struct to this field. */
  private int offset;

  /** The width (in bytes) of this field. */
  private int width;

  /** Default constructor. */
  public StructField(Position pos, String id, Type type, int offset, int width) {
    super(pos, id);
    this.type = type;
    this.offset = offset;
    this.width = width;
  }

  public Type getType() {
    return type;
  }

  public int getOffset() {
    return offset;
  }

  public int getWidth() {
    return width;
  }

  public void debugDump() {
    debug.Log.println(id + " :: " + type + " -- offset=" + offset + ", width=" + width);
  }
}