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
package mapreduce.framework.lab.matrix;

import static edu.wustl.cse231s.v5.V5.forall;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collector;
import java.util.stream.Collector.Characteristics;

import javax.annotation.concurrent.Immutable;

import edu.wustl.cse231s.NotYetImplementedException;
import edu.wustl.cse231s.util.MultiWrapMap;
import hash.studio.HashUtils;
import mapreduce.framework.core.MapReduceFramework;
import mapreduce.framework.core.Mapper;
import slice.core.IndexedRange;
import slice.studio.Slices;

/**
 * A MapReduce framework that uses a matrix to organize its keys and associated
 * mutable containers of values. Unlike the BottleneckedMapReduceFramework, this
 * class does not require the accumulation stage to run sequentially. It gets
 * around the issue by creating a whole matrix of {@code Map}s, where each row
 * is assigned to a map task, and each column is later assigned to a reduce
 * task.
 * 
 * The {@link #mapAndAccumulateAll(Object[])} method slices up the input and
 * maps each slice to one row, accumulating as it goes. It places items into the
 * correct column based on the {@link HashUtils#toIndex(Object, int)} method.
 * Then, the {@link #combineAndFinishAll(Map[][])} method combines the mutable
 * result containers from the various map tasks, and finishes them to the
 * reduced values.
 * 
 * @author Linus Dannull
 * @author Finn Voichick
 * @author Dennis Cosgrove (http://www.cse.wustl.edu/~cosgroved/)
 */
@Immutable
public class MatrixMapReduceFramework<E, K, V, A, R> implements MapReduceFramework<E, K, V, A, R> {
	private final Mapper<E, K, V> mapper;
	private final Collector<V, A, R> collector;
	private final int mapTaskCount;
	private final int reduceTaskCount;

	public MatrixMapReduceFramework(Mapper<E, K, V> mapper, Collector<V, A, R> collector, int mapTaskCount,
			int reduceTaskCount) {
		this.mapper = mapper;
		this.collector = collector;
		this.mapTaskCount = mapTaskCount;
		this.reduceTaskCount = reduceTaskCount;
	}

	public MatrixMapReduceFramework(Mapper<E, K, V> mapper, Collector<V, A, R> collector) {
		this(mapper, collector, Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().availableProcessors());
	}

	@Override
	public Mapper<E, K, V> getMapper() {
		return this.mapper;
	}

	@Override
	public Collector<V, A, R> getCollector() {
		return this.collector;
	}

	/**
	 * Should create the matrix used to store the mutable result containers. Each
	 * row in the matrix (using the first index of the 2D array) is assigned to a
	 * map task, and it should be a slice of the input. Each column in the matrix
	 * (using the second index) will be assigned to a reduce task, and it should be
	 * based on the hash of the key, using the
	 * {@link HashUtils#toIndex(Object, int)} method.
	 * 
	 * At each cell in this matrix is a Map<K,A>. Each key is emitted by the map
	 * method invocations, and each value is an accumulation of values emitted by
	 * the map method invocations. You will need to use the
	 * {@link Collector#supplier()} to provide these mutable result containers and
	 * the {@link Collector#accumulator()} to accumulate them.
	 * 
	 * @param input the original input of E items
	 * @return a 2D array holding all of mapped keys and their accumulations
	 * @throws InterruptedException if the computation was cancelled
	 * @throws ExecutionException   if the computation threw an exception
	 * @see IndexedRange
	 * @see Slices
	 * @see HashUtils
	 */
	private Map<K, A>[][] mapAndAccumulateAll(E[] input) throws InterruptedException, ExecutionException {
		List<IndexedRange> slices = Slices.createNSlices(input, mapTaskCount);

		@SuppressWarnings("unchecked")
		Map<K, A>[][] map = new HashMap[slices.size()][reduceTaskCount];

		for (int r = 0; r < slices.size(); r++) {
			for (int c = 0; c < reduceTaskCount; c++) {
				map[r][c] = new HashMap<>();
			}
		}

		forall(0, slices.size(), (row) -> {
			for (int col = slices.get(row).getMinInclusive(); col < slices.get(row).getMaxExclusive(); col++) {
				mapper.map(input[col], (key, val) -> {
					if (!map[row][HashUtils.toIndex(key, reduceTaskCount)].containsKey(key)) {
						// remember to check the toIndex'd col, not the [row][col], must check where you
						// actually placed the key/val/accumulator
						A supplier = collector.supplier().get();
						collector.accumulator().accept(supplier, val);
						map[row][HashUtils.toIndex(key, reduceTaskCount)].put(key, supplier);
					} else {
						collector.accumulator().accept(map[row][HashUtils.toIndex(key, reduceTaskCount)].get(key), val);
					}
				});
			}
		});
		return map;
	}

	/**
	 * Should use the matrix provided by the {@link #mapAndAccumulateAll(Object[])}
	 * method to reduce everything into a map from K to R. Each column should be
	 * processed in parallel. For this method, you will need to use the
	 * {@link Collector#combiner()} method to combine the mutable result containers
	 * row by row. You will also need to call the {@link Collector#finisher()} when
	 * you're done combining, to finish off the reductions. At the end, you will
	 * want to use the {@link MultiWrapMap} to wrap the finished results into a
	 * single map.
	 * 
	 * @param matrix the Map<K,A> matrix produced by the mapAndAccumulateAll method
	 * @return the final result, a map from K to R
	 * @see MultiWrapMap
	 * @throws InterruptedException if the computation was cancelled
	 * @throws ExecutionException   if the computation threw an exception
	 */
	private Map<K, R> combineAndFinishAll(Map<K, A>[][] matrix) throws InterruptedException, ExecutionException {
		@SuppressWarnings("unchecked")
		Map<K, R>[] intermediateArrayOfMaps = new Map[reduceTaskCount]; // array of maps for parallel combining
		for(int i = 0; i < intermediateArrayOfMaps.length; i++) {
			intermediateArrayOfMaps[i] = new HashMap<>(); 
		}
 
		
		forall(0, matrix.length, (col) -> { // go through each column, and combine and finish all the rows
			Map<K, A> tempMap = new HashMap<>(); 
			for(int row = 0; row < matrix[col].length; row++) {
				for(K key : matrix[row][col].keySet()) {
					if(!tempMap.containsKey(key)) {
						tempMap.put(key, matrix[row][col].get(key)); 
					}
					else {
						collector.combiner().apply(tempMap.get(key), matrix[row][col].get(key)); 
					}
				}
			}
			for(K key : tempMap.keySet()) {
				R r = collector.finisher().apply(tempMap.get(key)); 
				intermediateArrayOfMaps[col].put(key, r); 
			}
		});  
		
		Map<K, R> finishedMap = new MultiWrapMap<>(intermediateArrayOfMaps, (value) -> {
			return HashUtils.toIndex(value, reduceTaskCount); 
		});
		
		return finishedMap; 
		
		// The below code worked, but was not parallel and didn't use MultiWrapMap
//		//TODO MAKE PARALLEL
//		for(int col = 0; col < matrix.length; col++) {
////		forall(0, matrix.length, (col) -> {
//			for(int row = 0; row < matrix[col].length; row++) {
//				for(K key : matrix[row][col].keySet()) {
//					if(!tempMap.containsKey(key)) {
//						tempMap.put(key, matrix[row][col].get(key)); 
//					}
//					else {
//						collector.combiner().apply(tempMap.get(key), matrix[row][col].get(key)); 
//					}
//				}
//			}
////		});  
//		}
//		
//		
////		for(Entry<K, A> e : tempMap.entrySet()) {
////			R r = collector.finisher().apply(tempMap.get(e.getKey())); 
////			finishedMap.put(e.getKey(), r); 
////		}
//		return finishedMap; 

	}

	@Override
	public Map<K, R> mapReduceAll(E[] input) throws InterruptedException, ExecutionException {
		Map<K, A>[][] mapAndAccumulateAllResults = this.mapAndAccumulateAll(input);
		return this.combineAndFinishAll(mapAndAccumulateAllResults);
	}
}
