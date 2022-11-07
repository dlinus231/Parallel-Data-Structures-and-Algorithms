/*******************************************************************************
 * Copyright (C) 2016-2017 Dennis Cosgrove
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
package kmer.lab.concurrentbuckethashmap;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiFunction;

import edu.wustl.cse231s.NotYetImplementedException;
import hash.studio.HashUtils;
import edu.wustl.cse231s.util.KeyMutableValuePair;
import javax.annotation.concurrent.ThreadSafe;

/**
 * @author Linus Dannull
 * @author Dennis Cosgrove (http://www.cse.wustl.edu/~cosgroved/)
 */
@ThreadSafe
public class ConcurrentBucketHashMap<K, V> implements ConcurrentMap<K, V> {
	private final List<Entry<K, V>>[] buckets;
	private final ReadWriteLock[] locks;

	@SuppressWarnings("unchecked")
	public ConcurrentBucketHashMap(int bucketCount) {
		buckets = new List[bucketCount];
		locks = new ReadWriteLock[bucketCount];
		for (int i = 0; i < bucketCount; i++) {
			buckets[i] = new LinkedList<>();
			locks[i] = new ReentrantReadWriteLock();
		}
	}

	private List<Entry<K, V>> getBucket(Object key) {
		int location = HashUtils.toIndex(key, buckets.length);
		return buckets[location];
	}

	private ReadWriteLock getLock(Object key) {
		int location = HashUtils.toIndex(key, buckets.length);
		return locks[location];
	}

	private static <K, V> Optional<Entry<K, V>> getEntry(List<Entry<K, V>> bucket, Object key) {
		for (Entry<K, V> entry : bucket) {
			if (entry.getKey().equals(key)) {
				return Optional.of(entry);
			}
		}
		return null;
	}

	@Override
	public V get(Object key) {
		// try finally, finally will always run, even after a return statement
		getLock(key).readLock().tryLock();
		try {
			for (Entry<K, V> entry : getBucket(key)) {
				if (entry.getKey().equals(key)) {
					return entry.getValue();
				}
			}
		} finally {
			getLock(key).readLock().unlock();
		}
		return null;
	}

	@Override
	public V put(K key, V value) {
		getLock(key).writeLock().lock();
		try {
			// could make a variable for the bucket
			for (int i = 0; i < getBucket(key).size(); i++) {
				if (getBucket(key).get(i).getKey().equals(key)) { // if an Entry with the given key is present,
																	// overwrite the old value
					V returnVal = getBucket(key).get(i).getValue();
					getBucket(key).remove(i);
					getBucket(key).add(new KeyMutableValuePair<K, V>(key, value));
					return returnVal;
				}
			}
			getBucket(key).add(new KeyMutableValuePair<>(key, value));
			return null;
		} finally {
			locks[HashUtils.toIndex(key, buckets.length)].writeLock().unlock();
		}
	}

	@Override
	public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
		try {
			getLock(key).writeLock().lock(); 
			V oldValue; 
			if(getEntry(getBucket(key), key) != null) {
				oldValue = getEntry(getBucket(key), key).get().getValue();
			}
			else {
				oldValue = null; 
			}
			
			V newValue = remappingFunction.apply(key, oldValue);
			if (oldValue != null) {
				if (newValue != null) { // need to replace value
					// Do a for each because a get(i) with a fori loop makes this O(n^2), since get(i) is O(n) for LinkedLists
					for(Entry<K, V> e : getBucket(key)) {
						if(e.getKey().equals(key)) {
							e.setValue(newValue); 
							return newValue; 
						}
					}
					
//					for (int i = 0; i < getBucket(key).size(); i++) { // need to replace the oldValue
//						// use set
//						if (getBucket(key).get(i).getKey().equals(key)) {
//							getBucket(key).remove(i);
//							getBucket(key).add(new KeyMutableValuePair<>(key, newValue));
//							return newValue;
//						}
//					}
					return null;
				} 
				else { // newValue is null, so remove the originalEntry
					getBucket(key).remove(key);
					return null;
				}
			} else { // Old value is null, so there's no object with that key, add it
				if (newValue != null) {
					getBucket(key).add(new KeyMutableValuePair<>(key, newValue));
					return newValue;
				}
			}
			return null;
		}
		finally {
			getLock(key).writeLock().unlock(); 
		}
		
	}

	@Override
	public int size() {
		throw new RuntimeException("not required");
	}

	@Override
	public boolean isEmpty() {
		throw new RuntimeException("not required");
	}

	@Override
	public boolean containsKey(Object key) {
		throw new RuntimeException("not required");
	}

	@Override
	public boolean containsValue(Object value) {
		throw new RuntimeException("not required");
	}

	@Override
	public V putIfAbsent(K key, V value) {
		throw new RuntimeException("not required");
	}

	@Override
	public void putAll(Map<? extends K, ? extends V> m) {
		throw new RuntimeException("not required");
	}

	@Override
	public boolean replace(K key, V oldValue, V newValue) {
		throw new RuntimeException("not required");
	}

	@Override
	public V replace(K key, V value) {
		throw new RuntimeException("not required");
	}

	@Override
	public void clear() {
		throw new RuntimeException("not required");
	}

	@Override
	public boolean remove(Object key, Object value) {
		throw new RuntimeException("not required");
	}

	@Override
	public V remove(Object key) {
		throw new RuntimeException("not required");
	}

	@Override
	public Set<K> keySet() {
		throw new RuntimeException("not required");
	}

	@Override
	public Collection<V> values() {
		throw new RuntimeException("not required");
	}

	@Override
	public Set<Entry<K, V>> entrySet() {
		throw new RuntimeException("not required");
	}
}
