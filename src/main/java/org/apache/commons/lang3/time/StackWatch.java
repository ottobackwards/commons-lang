/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.commons.lang3.time;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedList;

/**
 * <p>
 * The {@code StackWatch}, provides a wrapper around the {@code StopWatch} for creating multiple and
 * possibly nested named timings.
 * </p>
 * <p>
 * While the {@link StopWatch} provides functionality to time the length of operations, there is no
 * context or name to go with the time tracked. It is also not possible to time nested calls with
 * the {@code StopWatch}.
 * </p>
 * <p>
 * {@code StackWatch} provides that functionality, allowing successive calls to
 * {@link StackWatch#startTiming(String, Collection)} to track nested calls.
 * </p>
 * <p>
 * Each start provides a timing name and a parent timing name, thus providing context to the timing.
 * </p>
 * <p>
 * At the end of a timing 'run', a visitor interface provides the ability to visit all the timing
 * 'nodes' and capture their output, including the level of the call if nested.
 * </p>
 * <p>
 * The {@code TimeRecordNodes} provide a tree structure in support of nesting.
 * A {@code Deque} is use to track the current time node.
 * </p>
 * <pre>
 *   <code>
 *    private void outerFunction() {
 *      try {
 *        StackWatch watch = new StackWatch("OuterFunction");
 *        watch.start();
 *        functionOne(watch);
 *        watch.stop();
 *        watch.visit(new TimingRecordNodeVisitor() {
 *         {@literal @}Override
 *          public void visitRecord(int level, TimingRecordNode node) {
 *            System.out.println("Visit level " + level + " node: " + node.getPath());
 *          }
 *        });
 *      } catch (Exception e){}
 *    }
 *
 *    private void functionOne(StackWatch watch) throws Exception {
 *      watch.startTiming("One", "OneFunc");
 *      functionOneOne(watch);
 *      watch.stopTiming();
 *    }
 *
 *    private void functionOneOne(StackWatch watch) throws Exception {
 *      watch.startTiming("OneOne", "OneFunc");
 *      functionOneTwo(watch);
 *      watch.stopTiming();
 *    }
 *
 *    private void functionOneTwo(StackWatch watch) throws Exception {
 *      watch.startTiming("OneTwo", "OneFunc");
 *      watch.stopTiming();
 *    }
 *   </code>
 * </pre>
 * <p>
 * The example above would result in the following output.
 * </p>
 * <pre>
 *   <code>
 *    Visit level 0 node: OuterFunction
 *    Visit level 1 node: OuterFunction/One
 *    Visit level 2 node: OuterFunction/One/OneOne
 *    Visit level 3 node: OuterFunction/One/OneOne/OneTwo
 *   </code>
 * </pre>
 * <p>
 * This class is not thread safe, and is meant to track timings across multiple calls on the same
 * thread
 * </p>
 *
 * @since 3.8
 */
public class StackWatch<T> {

    /**
     * The default name for the root level timing if not provided
     */
    public static final String DEFAULT_ROOT_NAME = "ROOT_TIMING";

    /**
     * The Deque used to track the timings
     */
    private Deque<TimingRecordNode<T>> deque = new LinkedList<>();

    /**
     * The name of the root node
     */
    private String rootName = DEFAULT_ROOT_NAME;

    /**
     * The root {@code TimingRecordNode}.
     * The root node represents the root of a tree structure, and contains all {@code TimingRecordNode}
     * instances.
     */
    private TimingRecordNode<T> rootNode;

    /**
     * <p>
     * Constructor
     * </p>
     * <p>
     * The top most timing will be created with the rootName on {@link StackWatch#start()} ()}
     * </p>
     * If the passed name is empty, the DEFAULT_ROOT_NAME {@value DEFAULT_ROOT_NAME} will be used.
     *
     * @param rootName the root name
     */
    public StackWatch(String rootName) {
        if (!StringUtils.isBlank(rootName)) {
            this.rootName = rootName;
        }
    }

    /**
     * <p>
     * Constructor
     * </p>
     * <p>
     * The DEFAULT_ROOT_NAME {@value DEFAULT_ROOT_NAME} will be used.
     * </p>
     */
    public StackWatch() {
    }

    /**
     * <p>
     * Returns the root name.
     * </p>
     *
     * @return the root name.
     */
    public String getRootName() {
        return this.rootName;
    }

    /**
     * <p>
     * Starts the {@code StackWatch}.
     * </p>
     * <p>
     * A root timing will be created named for the rootName and started.
     * </p>
     * <p>
     * If not called before the first {@link  StackWatch#startTiming(String, Collection)} call, then the
     * {@code StackWatch} will be started at that time.
     * </p>
     *
     * @throws IllegalStateException if the {@code StackWatch} has already been started.
     */
    public void start() {
        if (rootNode != null) {
            throw new IllegalStateException("StackWatch has already been started");
        }
        rootNode = new TimingRecordNode<>(null, rootName, new ArrayList<T>());
        rootNode.start();
        deque.push(rootNode);
    }

    /**
     * <p>
     * Start a timing.  If {@link StackWatch#start()} has not been called, the {@code StackWatch} will be
     * started.
     * </p>
     * <p>
     * This may be called multiple times, before a {@link StackWatch#stopTiming()} call is made, if calls are nested,
     * for example:
     * </p>
     * <pre>
     *   <code>
     *    private void functionOne(StackWatch watch) throws Exception {
     *      watch.startTiming("One", "OneFunc");
     *      functionOneOne(watch);
     *      watch.stopTiming();
     *    }
     *
     *    private void functionOneOne(StackWatch watch) throws Exception {
     *      watch.startTiming("OneOne", "OneFunc");
     *      functionOneTwo(watch);
     *      watch.stopTiming();
     *    }
     *
     *    private void functionOneTwo(StackWatch watch) throws Exception {
     *      watch.startTiming("OneTwo", "OneFunc");
     *      watch.stopTiming();
     *    }
     *   </code>
     * </pre>
     * <p>
     * Starting a timing, when it's parent timing is not running results in an
     * {@code IllegalStateException}.
     * </p>
     * <p>
     * For example, this code, although contrived, would throw an {@code IllegalStateException}, because
     * functionOne is not running:
     * </p>
     * <pre>
     *   <code>
     *    private void functionOne(StackWatch watch) throws Exception {
     *      watch.startTiming("One", "OneFunc");
     *      watch.visit(new TimingRecordNodeVisitor() {
     *       {@literal @}Override
     *        public void visitRecord(int level, TimingRecordNode node) {
     *          node.getStopWatch().stop();
     *        }
     *      });
     *      functionOneOne(watch);
     *    }
     *
     *    private void functionOneOne(StackWatch watch) throws Exception {
     *      watch.startTiming("OneOne", "OneFunc");
     *      functionOneTwo(watch);
     *      watch.stopTiming();
     *    }
     *   </code>
     * </pre>
     * <p>
     * Starting a timing, when some number of timings have been started and all closed results in an
     * {@code IllegalStateException}.
     * </p>
     * <p>
     * For example:
     * </p>
     * <pre>
     *  <code>
     *    StackWatch watch = new StackWatch("testStackWatch");
     *    watch.startTiming("Test");
     *    functionOne(watch);
     *    watch.stopTiming();
     *    watch.stop();
     *    watch.startTiming("More Test");
     *  </code>
     * </pre>
     *
     * @param name the name of this timing
     * @throws IllegalStateException if the parent timing is not running or there is an attempt to start
     *                               a new timing after creating a number of timings and closing them all.
     */
    public void startTiming(String name) {
        startTiming(name, new ArrayList<T>());
    }

    /**
     * <p>
     * Start a timing.  If {@link StackWatch#start()} has not been called, the {@code StackWatch} will be
     * started.
     * </p>
     * <p>
     * This may be called multiple times, before a {@link StackWatch#stopTiming()} call is made, if calls are nested,
     * for example:
     * </p>
     * <pre>
     *   <code>
     *    private void functionOne(StackWatch watch) throws Exception {
     *      watch.startTiming("One", "OneFunc");
     *      functionOneOne(watch);
     *      watch.stopTiming();
     *    }
     *
     *    private void functionOneOne(StackWatch watch) throws Exception {
     *      watch.startTiming("OneOne", "OneFunc");
     *      functionOneTwo(watch);
     *      watch.stopTiming();
     *    }
     *
     *    private void functionOneTwo(StackWatch watch) throws Exception {
     *      watch.startTiming("OneTwo", "OneFunc");
     *      watch.stopTiming();
     *    }
     *   </code>
     * </pre>
     * <p>
     * Starting a timing, when it's parent timing is not running results in an
     * {@code IllegalStateException}.
     * </p>
     * <p>
     * For example, this code, although contrived, would throw an {@code IllegalStateException}, because
     * functionOne is not running:
     * </p>
     * <pre>
     *   <code>
     *    private void functionOne(StackWatch watch) throws Exception {
     *      watch.startTiming("One", "OneFunc");
     *      watch.visit(new TimingRecordNodeVisitor() {
     *       {@literal @}Override
     *        public void visitRecord(int level, TimingRecordNode node) {
     *          node.getStopWatch().stop();
     *        }
     *      });
     *      functionOneOne(watch);
     *    }
     *
     *    private void functionOneOne(StackWatch watch) throws Exception {
     *      watch.startTiming("OneOne", "OneFunc");
     *      functionOneTwo(watch);
     *      watch.stopTiming();
     *    }
     *   </code>
     * </pre>
     * <p>
     * Starting a timing, when some number of timings have been started and all closed results in an
     * {@code IllegalStateException}.
     * </p>
     * <p>
     * For example:
     * </p>
     * <pre>
     *  <code>
     *    StackWatch watch = new StackWatch("testStackWatch");
     *    watch.startTiming("Test");
     *    functionOne(watch);
     *    watch.stopTiming();
     *    watch.stop();
     *    watch.startTiming("More Test");
     *  </code>
     * </pre>
     *
     * @param name the name of this timing
     * @param tags the tags to associate with this timing
     * @throws IllegalStateException if the parent timing is not running or there is an attempt to start
     *                               a new timing after creating a number of timings and closing them all.
     */
    public void startTiming(String name, Collection<? extends T> tags) {
        final TimingRecordNode<T> parentNode;
        // If the deque is empty, then the root needs to be added and started
        if (deque.isEmpty()) {
            // If it already exists, it means that all the timings were closed and a new timing was started.
            // If this happens, it will result in an IllegalStateException
            if (rootNode != null) {
                throw new IllegalStateException(
                        "Attempting to start a second set of timings, StackWatch must"
                                + " be cleared first");
            }
            // create, add, and start the root node
            start();
            parentNode = rootNode;
        } else {
            // if the current node is not running, then this is an InvalidStateException, as the parent
            // cannot close before it's children
            if (!deque.peek().isRunning()) {
                throw new IllegalStateException(String
                        .format("Parent TimingRecordNode %s is not running", deque.peek().getPath()));
            }
            // this is a nested start, add as child to current running
            parentNode = deque.peek();
        }

        // request the current node to create a new child with this timing name and start it
        TimingRecordNode<T> node = parentNode.createChild(name, tags);
        node.start();
        // this node is now top of the stack
        deque.push(node);
    }

    /**
     * <p>
     * Stop the current timing.
     * </p>
     * <p>
     * In the case of nested timings, the current timing is stopped and removed from the {@code Deque}
     * causing the parent node to be the top of the stack.
     * </p>
     * <p>
     * If the timing being stopped has running child timings an {@code IllegalStateException} will
     * be thrown.
     * </p>
     *
     * @throws IllegalStateException if stopping a timing with running child timings
     */
    public void stopTiming() {
        if (deque.isEmpty()) {
            throw new IllegalStateException(
                    "Trying to stop time, there are no running records in deque");
        }
        deque.pop().stop();
    }

    /**
     * Stops the {@code StackWatch}.
     *
     * @throws IllegalStateException if there are running timings other than the root timing
     */
    public void stop() {
        if (deque.size() > 1) {
            throw new IllegalStateException("Stopping with running timings");
        }
        stopTiming();
    }

    /**
     * Clears the stack and the root node.
     */
    public void clear() {
        deque.clear();
        rootNode = null;
    }

    /**
     * <p>
     * Initiate the visitation of the nodes in this timing.
     * </p>
     * <p>
     * The {@code TimingRecordNodeVisitor} will be called back for each node in the tree, and will
     * pass the level of the node in the tree. The root level is 0.
     * </p>
     *
     * @param visitor callback interface.
     */
    public void visit(TimingRecordNodeVisitor<T> visitor) {
        rootNode.visit(0, visitor);
    }
}
