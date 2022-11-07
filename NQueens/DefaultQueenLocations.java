/*******************************************************************************
 * Copyright (C) 2016-2020 Dennis Cosgrove
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 ******************************************************************************/
package nqueens.lab;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import edu.wustl.cse231s.NotYetImplementedException;
import nqueens.core.QueenLocations;

/**
 * @author Linus Dannull
 * @author Dennis Cosgrove (http://www.cse.wustl.edu/~cosgroved/)
 */
@Immutable
public final class DefaultQueenLocations implements QueenLocations {
	private final Optional<Integer>[] locations;

	@SuppressWarnings("unchecked")
	public DefaultQueenLocations(int boardSize) {
		locations = new Optional[boardSize];
		for (int row = 0; row < this.locations.length; ++row) {
			locations[row] = Optional.empty();
		}
	}

	@SuppressWarnings("unchecked")
	private DefaultQueenLocations(DefaultQueenLocations other, int row, int col) {
		if (other.isLocationThreatFree(row, col)) {
			int boardSize = other.getBoardSize();
			if (row < 0 || row >= boardSize) {
				throw new IllegalArgumentException("row " + row + " is not in range [0, " + boardSize + ")");
			}
			if (col < 0 || col >= boardSize) {
				throw new IllegalArgumentException("col " + col + " is not in range [0, " + boardSize + ")");
			}
			locations = new Optional[boardSize];
			for (int r = 0; r < other.getBoardSize(); ++r) {
				if (r == row) {
					locations[row] = Optional.of(col);
				} else {
					locations[r] = other.getColumnOfQueenInRow(r);
				}
			}
		} else {
			throw new IllegalArgumentException(
					"Not threat free: (row=" + row + ", column=" + col + ")\nOther board:\n" + other.toString());
		}
	}

	@Override
	public DefaultQueenLocations createNext(int row, int col) {
		return new DefaultQueenLocations(this, row, col); 
	}


	@Override
	public int getBoardSize() {
		return locations.length; 
	}

	@Override
	public Optional<Integer> getColumnOfQueenInRow(int row) {
		return locations[row];
	}

	@Override
	public List<Integer> getCandidateColumnsInRow(int row) {
		List<Integer> returnList = new LinkedList<>(); 
		for(int c = 0; c < this.getBoardSize(); c++) {
			if(isLocationThreatFree(row, c)) {
				returnList.add(c); 
			}				
		}
		return returnList; 
	}

	@Override
	public boolean isLocationThreatFree(int row, int col) {
		int boardSize = this.getBoardSize();
		for (int r = 0; r < boardSize; ++r) {
			Optional<Integer> columnOfQueenInRowR = this.getColumnOfQueenInRow(r);
			if (columnOfQueenInRowR.isPresent()) {
				int c = columnOfQueenInRowR.get();

				// is in same row
				if (r == row) {
					// note: we do not check if it is the same column, we return false
					return false;
				}

				// is in same column
				if (c == col) {
					return false;
				}

				// is in same diagonal A
				if (row - r == c - col) {
					return false;
				}

				// is in same diagonal B
				if (row - r == col - c) {
					return false;
				}
			}
		}
		return true;
	}

	@Override
	public String toString() {
		StringBuilder result = new StringBuilder();
		int boardSize = this.getBoardSize();

		int boardSizeMinusOneStringLength = Integer.toString(boardSize - 1).length();

		for (int i = 0; i < boardSizeMinusOneStringLength; ++i) {
			result.append(" ");
		}
		result.append("  + ");
		for (int c = 0; c < boardSize; ++c) {
			result.append("- ");
		}
		result.append("+\n");
		for (int r = boardSize - 1; r >= 0; --r) {
			result.append("r");
			result.append(String.format("%0" + boardSizeMinusOneStringLength + "d", r));
			result.append(" | ");
			Optional<Integer> col = getColumnOfQueenInRow(r);
			for (int c = 0; c < boardSize; ++c) {
				if (col.isPresent() && col.get() == c) {
					result.append("Q");
				} else {
					if (c % 2 == r % 2) {
						result.append(" ");
					} else {
						result.append("*");
					}
				}
				result.append(" ");
			}
			result.append("|\n");
		}
		for (int i = 0; i < boardSizeMinusOneStringLength; ++i) {
			result.append(" ");
		}
		result.append("  + ");
		for (int c = 0; c < boardSize; ++c) {
			result.append("- ");
		}
		result.append("+\n");
		return result.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(locations);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		DefaultQueenLocations other = (DefaultQueenLocations) obj;
		if (!Arrays.equals(locations, other.locations))
			return false;
		return true;
	}
}
