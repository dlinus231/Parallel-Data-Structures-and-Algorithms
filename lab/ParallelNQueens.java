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

import static edu.wustl.cse231s.v5.V5.async;
import static edu.wustl.cse231s.v5.V5.finish;
import static edu.wustl.cse231s.v5.V5.forasync;
import static edu.wustl.cse231s.v5.V5.newIntegerFinishAccumulator;
import static edu.wustl.cse231s.v5.V5.register;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import edu.wustl.cse231s.NotYetImplementedException;
import edu.wustl.cse231s.v5.api.CheckedRunnable;
import edu.wustl.cse231s.v5.api.FinishAccumulator;
import edu.wustl.cse231s.v5.api.NumberReductionOperator;
import nqueens.core.QueenLocations;
import nqueens.core.RowSearchOrder;

/**
 * @author Linus Dannull
 * @author Dennis Cosgrove (http://www.cse.wustl.edu/~cosgroved/)
 */
public class ParallelNQueens {
	/**
	 * @param accumulator        tracks the number of solutions found so far.
	 * @param queenLocations     the current state of the chess board.
	 * @param rowSearchAlgorithm responsible for selecting the next unplaced row
	 * @throws InterruptedException if the computation was cancelled
	 * @throws ExecutionException   if the computation threw an exception
	 */
	private static void searchForSolutions(FinishAccumulator<Integer> accumulator, QueenLocations queenLocations,
			RowSearchOrder rowSearchAlgorithm) throws InterruptedException, ExecutionException {

		// if the nextUnplacedRow is OptionalEmpty(), we've found a solution
		if (rowSearchAlgorithm.selectedNextUnplacedRow(queenLocations).equals(Optional.empty())) {
			// if there are no next rows, we've filled board, increment accumulator
			accumulator.put(1);
		} 
		else {
			int r = rowSearchAlgorithm.selectedNextUnplacedRow(queenLocations).get();
			List<Integer> cols = queenLocations.getCandidateColumnsInRow(r); 
			forasync(cols, (c) -> {
				QueenLocations nextBoard = queenLocations.createNext(r, c);
				searchForSolutions(accumulator, nextBoard, rowSearchAlgorithm);
			}); 
			
			// Below is an alternate slower solution (almost 2x slower), 
			// because I check if every square is a threat in each iteration 
			// instead of using the getCandidateColumnsInRow() method
			
//			forasync(0, queenLocations.getBoardSize(), (c) -> {
//				int r = rowSearchAlgorithm.selectedNextUnplacedRow(queenLocations).get();
//				List<Integer> cols = queenLocations.getCandidateColumnsInRow(r); 
//				if (queenLocations.isLocationThreatFree(r, c)) {
//					// if location is threat free
//					QueenLocations nextBoard = queenLocations.createNext(r, c);
//					searchForSolutions(accumulator, nextBoard, rowSearchAlgorithm);
//				}
//			});
			
		}
//		if(row == board.length) { // if we've reached a solution
//			count.increment();
//		}
//		else {
//			for(int c = 0; c < board.length; c++) {
//				if(isLocationThreatFree(board, row, c)) {
//					board[row] = c; // place the queen
//					search(count, board, row+1); // go up the board
//					board[row] = EMPTY; // the backtrack
//				}
//			}
//		}
	}

	/**
	 * @param queenLocations A chess board to count the solutions.
	 * @return The number of solutions found.
	 * @throws InterruptedException if the computation was cancelled
	 * @throws ExecutionException   if the computation threw an exception
	 */
	public static int countSolutions(QueenLocations queenLocations, RowSearchOrder rowSearchAlgorithm)
			throws InterruptedException, ExecutionException {
		FinishAccumulator<Integer> acc = newIntegerFinishAccumulator(NumberReductionOperator.SUM);
		finish(register(acc), () -> {
			searchForSolutions(acc, queenLocations, rowSearchAlgorithm);
		});
		return acc.get();
	}
}
