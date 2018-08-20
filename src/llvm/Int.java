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
package llvm;


/** Represents an LLVM integer constant value. */
public class Int extends Value {

  /** The numeric value associated with this Int. */
  private long num;

  /** Default constructor. */
  public Int(long num) {
    this.num = num;
  }

  /** The integer constant with all bits zero. */
  public static final Int ZERO = new Int(0);

  /** The integer constant with all bits one. */
  public static final Int ONES = new Int(~0);

  /** Return the LLVM type of this value. */
  public Type getType() {
    return Type.i32;
  }

  /** Append the name for this value to the specified buffer. */
  public void appendName(StringBuilder buf) {
    buf.append(num);
  }
}
