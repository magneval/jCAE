/* jCAE stand for Java Computer Aided Engineering. Features are : Small CAD
   modeler, Finite element mesher, Plugin architecture.

    Copyright (C) 2006, by EADS CRC
    Copyright (C) 2007, by EADS France

    This library is free software; you can redistribute it and/or
    modify it under the terms of the GNU Lesser General Public
    License as published by the Free Software Foundation; either
    version 2.1 of the License, or (at your option) any later version.

    This library is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
    Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public
    License along with this library; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA */

package org.jcae.mesh.amibe.ds;

import org.jcae.mesh.amibe.traits.Traits;
import org.jcae.mesh.amibe.traits.HalfEdgeTraitsBuilder;
import java.util.Iterator;
import java.util.Map;

/**
 * Abstract class to define common methods on edges.
 * We use an half-edge structure to perform mesh traversal.  Vertices can be
 * obtained by {@link #origin}, {@link #destination} and {@link #apex}, and the
 * triangle found at the left of an edge is given by {@link #getTri}.
 *
 * <h2>Geometrical primitives</h2>
 *
 * <p>
 * Consider the <code>AbstractHalfEdge</code> edge <code>e</code> between vertices
 * <em>A</em> and <em>B</em>, starting from <em>A</em>, in image below:
 * </p>
 * <p align="center"><img src="doc-files/AbstractHalfEdge-1.png" alt="[Drawing to illustrate geometrical primitives]"/></p>
 * <p>
 * The following methods can be applied to <code>e</code>:
 * </p>
 * <ul>
 *    <li><code>e.{@link #next}</code> and <code>e.{@link #prev}</code> get respectively next and previous
 *        edges in the same triangle <em>(ABC)</em> in a counterclockwise cycle.</li>
 *    <li><code>e.{@link #sym}</code> gets the opposite <code>AbstractHalfEdge</code>.</li>
 *    <li><code>e.{@link #nextOrigin}</code> returns next edge starting from the same origin <em>A</em>
 *        when cycling counterclockwise around <em>A</em>.</li>
 * </ul>
 * <p><strong>Warning:</strong>
 * As {@link VirtualHalfEdge} instances are handles to edges and not physical objects, these methods
 * modify current instance.  Another set of methods is defined to apply these transformations to
 * another instance, so that current instance is not modified.
 * </p>
 * <ul>
 *    <li><code>e.<a href="#next(org.jcae.mesh.amibe.ds.AbstractHalfEdge)">next</a>(f)</code> and
 *        <code>e.<a href="#prev(org.jcae.mesh.amibe.ds.AbstractHalfEdge)">prev</a>(f)</code>
 *        move <code>f</code> respectively to next and previous edges in the same triangle <em>(ABC)</em>
 *        in a counterclockwise cycle.</li>
 *    <li><code>e.<a href="#sym(org.jcae.mesh.amibe.ds.AbstractHalfEdge)">sym</a>(f)</code>
 *        moves <code>f</code> to opposite of <code>e</code>.</li>
 *    <li><code>e.<a href="#nextOrigin(org.jcae.mesh.amibe.ds.AbstractHalfEdge)">nextOrigin</a>(f)</code>
 *        moves <code>f</code> to the next edge starting from the same origin <em>A</em> when
 *        cycling counterclockwise around <em>A</em>.</li>
 * </ul>
 *
 * <p>
 * For convenience, derived classes may also define the following methods, which
 * are combinations of previous ones:
 * </p>
 * <ul>
 *    <li><code>e.prevOrigin()</code> moves counterclockwise to the previous edge
 *        starting from the same origin.</li>
 *    <li><code>e.nextDest()</code> and <code>e.prevDest()</code> move counterclockwise
 *        to the next (resp. previous) edge with the same destination vertex <em>B</em>.</li>
 *    <li><code>e.nextApex()</code> and <code>e.prevApex()</code> move counterclockwise
 *        to the next (resp. previous) edge with the same apical vertex <em>C</em>.</li>
 * </ul>
 *
 * <h2>Mesh Operations</h2>
 * <p>
 * These operations are abstract methods and are implemented by subclasses:
 * </p>
 * <dl>
 *   <dt>{@link #swap}</dt>
 *   <dd>Swaps an edge.  Returned value has the same original and apical vertices as
 *       original edge.  Triangles and edges are modified, objects are not destroyed and
 *       inserted into mesh.
 *       <p align="center"><img src="doc-files/AbstractHalfEdge-4.png" alt="[Image showing edge swap]"/></p>
 *   </dd>
 *   <dt>{@link #split}</dt>
 *   <dd>Splits a vertex to create a new edge.  In figure below, <em>A</em> is duplicated into <em>N</em>,
 *       and two new triangles are created.  Returned value has the same original and apical vertices as
 *       original edge, and its destination vertex is <em>N</em>.
 *       <p><strong>Warning:</strong> This method does not check that new triangles are not inverted.</p>
 *       <p align="center"><img src="doc-files/AbstractHalfEdge-3.png" alt="[Image showing vertex split]"/></p>
 *   </dd>
 *   <dt>{@link #collapse}</dt>
 *   <dd>Collapses an edge into a new point.  Triangles, edges and vertices are removed from mesh
 *       and replaced by new objects.  New point may be origin or destination points.
 *       Returned value has the new point as its origin, and its apex is the same as in original edge.
 *       When <em>N</em> is <em>A</em>,
 *       <code><a href="#collapse(org.jcae.mesh.amibe.ds.AbstractMesh, org.jcae.mesh.amibe.ds.AbstractVertex)">collapse</a></code>
 *       is the opposite of 
 *       <code><a href="#split(org.jcae.mesh.amibe.ds.AbstractMesh, org.jcae.mesh.amibe.ds.AbstractVertex)">split</a></code>.
 *       <p><strong>Warning:</strong> This method does not check that triangles are not inverted.
 *       Method {@link #canCollapse} <strong>must</strong> have been called to
 *       ensure that this edge collapse is possible, otherwise errors may
 *       occur.  This method is not called automatically because it is
 *       sometimes costful.</p>
 *       <p align="center"><img src="doc-files/AbstractHalfEdge-2.png" alt="[Image showing edge collapse]"/></p>
 *   </dd>
 * </dl>
 * <p>
 * Returned values have been chosen so that these methods always return an edge
 * which has the same apical vertex.  As will be explained below, they gracefully
 * work with boundaries and non-manifold meshes.
 * </p>
 *
 * <p>
 * These two methods may also be of interest when writing new algorithms:
 * </p>
 * <dl>
 *   <dt>{@link #canCollapse}</dt>
 *   <dd>Tells whether an edge can be collapsed and replaced by the given vertex.</dd>
 *   <dt>{@link #checkNewRingNormals}</dt>
 *   <dd>Tells whether triangles become inverted if origin point is moved at
 *       given location.</dd>
 * </dl>
 *
 * <h2>Boundaries</h2>
 *
 * <h2>Non-manifold meshes</h2>
 *
 */
public abstract class AbstractHalfEdge
{
	//  User-defined traits
	protected final HalfEdgeTraitsBuilder traitsBuilder;
	protected final Traits traits;

	/**
	 * Numeric constants for edge attributes.  Set if edge is on
	 * boundary.
	 * @see #setAttributes
	 * @see #hasAttributes
	 * @see #clearAttributes
	 */
	public static final int BOUNDARY = 1 << 0;
	/**
	 * Numeric constants for edge attributes.  Set if edge is outer.
	 * (Ie. one of its end point is {@link Mesh#outerVertex})
	 * @see #setAttributes
	 * @see #hasAttributes
	 * @see #clearAttributes
	 */
	public static final int OUTER    = 1 << 1;
	/**
	 * Numeric constants for edge attributes.  Set if edge had been
	 * swapped.
	 * @see #setAttributes
	 * @see #hasAttributes
	 * @see #clearAttributes
	 */
	public static final int SWAPPED  = 1 << 2;
	/**
	 * Numeric constants for edge attributes.  Set if edge had been
	 * marked (for any operation).
	 * @see #setAttributes
	 * @see #hasAttributes
	 * @see #clearAttributes
	 */
	public static final int MARKED   = 1 << 3;
	/**
	 * Numeric constants for edge attributes.  Set if edge is the inner
	 * edge of a quadrangle.
	 * @see #setAttributes
	 * @see #hasAttributes
	 * @see #clearAttributes
	 */
	public static final int QUAD     = 1 << 4;
	/**
	 * Numeric constants for edge attributes.  Set if edge is non
	 * manifold.
	 * @see #setAttributes
	 * @see #hasAttributes
	 * @see #clearAttributes
	 */
	public static final int NONMANIFOLD = 1 << 5;
	
	protected static final Integer [] int3 = new Integer[3];
	static {
		int3[0] = Integer.valueOf(0);
		int3[1] = Integer.valueOf(1);
		int3[2] = Integer.valueOf(2);
	}

	public AbstractHalfEdge()
	{
		traitsBuilder = null;
		traits = null;
	}
	public AbstractHalfEdge(HalfEdgeTraitsBuilder builder)
	{
		traitsBuilder = builder;
		if (builder != null)
			traits = builder.createTraits();
		else
			traits = null;
	}

	/**
	 * Moves to symmetric edge.
	 * @return  current instance after its transformation
	 */
	public abstract AbstractHalfEdge sym();

	/**
	 * Moves to symmetric edge.
	 * Make <code>that</code> instance be a copy of current
	 * instance, move it to its symmetric edge and return
	 * this instance.  Current instance is not modified.
	 *
	 * @param  that  instance where transformed edge is stored
	 * @return   argument after its transformation
	 */
	public abstract AbstractHalfEdge sym(AbstractHalfEdge that);

	/**
	 * Moves counterclockwise to following edge.
	 * @return  current instance after its transformation
	 */
	public abstract AbstractHalfEdge next();

	/**
	 * Moves counterclockwise to following edge.
	 * Make <code>that</code> instance be a copy of current
	 * instance, move it counterclockwise to next edge and
	 * return this instance.  Current instance is not modified.
	 *
	 * @param  that  instance where transformed edge is stored
	 * @return   argument after its transformation
	 */
	public abstract AbstractHalfEdge next(AbstractHalfEdge that);

	/**
	 * Moves counterclockwise to previous edge.
	 * @return  current instance after its transformation
	 */
	public abstract AbstractHalfEdge prev();

	/**
	 * Moves counterclockwise to previous edge.
	 * Make <code>that</code> instance be a copy of current
	 * instance, move it counterclockwise to previous edge and
	 * return this instance.  Current instance is not modified.
	 *
	 * @param  that  instance where transformed edge is stored
	 * @return   argument after its transformation
	 */
	public abstract AbstractHalfEdge prev(AbstractHalfEdge that);

	/**
	 * Moves counterclockwise to the following edge which has the same origin.
	 * @return  current instance after its transformation
	 */
	public abstract AbstractHalfEdge nextOrigin();

	/**
	 * Moves counterclockwise to the following edge which has the same origin.
	 * Make <code>that</code> instance be a copy of current
	 * instance, move it counterclockwise to the following edge which
	 * has the same origin and return this instance.  Current instance is
	 * not modified.
	 *
	 * @param  that  instance where transformed edge is stored
	 * @return   argument after its transformation
	 */
	public abstract AbstractHalfEdge nextOrigin(AbstractHalfEdge that);

	public abstract AbstractHalfEdge nextOriginLoop();

	public abstract int getLocalNumber();
	public abstract Triangle getTri();
	public abstract Object getAdj();
	public abstract Map<Triangle, Integer> getAdjNonManifold();
	public abstract void setAdj(Object link);
	public abstract Vertex origin();
	public abstract Vertex destination();
	public abstract Vertex apex();
	public abstract void setAttributes(int attr);
	public abstract void clearAttributes(int attr);
	public abstract boolean hasAttributes(int attr);
	/**
	 * Swaps an edge.
	 *
	 * @return swapped edge, origin and apical vertices are the same as in original edge
	 * @throws IllegalArgumentException if edge is on a boundary or belongs
	 * to an outer triangle.
	 * @see Mesh#edgeSwap
	 */
	protected abstract AbstractHalfEdge swap();

	/**
	 * Checks that triangles are not inverted if origin vertex is moved.
	 *
	 * @param newpt  the new position to be checked.
	 * @return <code>false</code> if the new position produces
	 *    an inverted triangle, <code>true</code> otherwise.
	 */
	public abstract boolean checkNewRingNormals(double [] newpt);

	/**
	 * Checks whether an edge can be contracted into a given vertex.
	 *
	 * @param n  the resulting vertex
	 * @return <code>true</code> if this edge can be contracted into the single vertex n, <code>false</code> otherwise.
	 * @see Mesh#canCollapseEdge
	 */
	protected abstract boolean canCollapse(AbstractVertex n);

	/**
	 * Contracts an edge.
	 *
	 * @param m  mesh
	 * @param n  the resulting vertex
	 * @return edge starting from <code>n</code> and with the same apex
	 * @throws IllegalArgumentException if edge belongs to an outer triangle,
	 * because there would be no valid return value.  User must then run this
	 * method against symmetric edge, this is not done automatically.
	 * @see Mesh#edgeCollapse
	 */
	protected abstract AbstractHalfEdge collapse(AbstractMesh m, AbstractVertex n);

	/**
	 * Splits an edge.  This is the opposite of {@link #collapse}.
	 *
	 * @param m  mesh
	 * @param n  the resulting vertex
	 * @return edge starting from <code>n</code> and pointing to original apex
	 * @see Mesh#vertexSplit
	 */
	protected abstract AbstractHalfEdge split(AbstractMesh m, AbstractVertex n);

	/**
	 * Sets the edge tied to this object.
	 *
	 * @param e  the edge tied to this object
	 */
	public abstract void glue(AbstractHalfEdge e);

	/**
	 * Returns the area of this triangle.
	 *
	 * @return the area of this triangle.
	 */
	public abstract double area();

	public abstract Iterator<AbstractHalfEdge> fanIterator();

}
