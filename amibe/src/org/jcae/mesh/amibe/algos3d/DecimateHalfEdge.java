/* jCAE stand for Java Computer Aided Engineering. Features are : Small CAD
   modeler, Finit element mesher, Plugin architecture.

    Copyright (C) 2003 Jerome Robert <jeromerobert@users.sourceforge.net>

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
    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.jcae.mesh.amibe.algos3d;

import org.jcae.mesh.amibe.ds.Mesh;
import org.jcae.mesh.amibe.ds.HalfEdge;
import org.jcae.mesh.amibe.ds.Triangle;
import org.jcae.mesh.amibe.ds.OTriangle;
import org.jcae.mesh.amibe.ds.Vertex;
import org.jcae.mesh.amibe.metrics.Metric3D;
import org.jcae.mesh.amibe.metrics.Matrix3D;
import org.jcae.mesh.amibe.util.PAVLSortedTree;
import org.jcae.mesh.xmldata.MeshReader;
import org.jcae.mesh.xmldata.MeshWriter;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.ArrayList;
import org.apache.log4j.Logger;

/**
 * Decimates a mesh.  This method is based on Michael Garland's work on
 * <a href="http://graphics.cs.uiuc.edu/~garland/research/quadrics.html">quadric error metrics</a>.
 *
 * <p>
 * A plane is fully determined by its normal <code>N</code> and the signed
 * distance <code>d</code> of the frame origin to this plane, or in other 
 * words the equation of this plane is <code>tN V + d = 0</code>.
 * The squared distance of a point to this plane is
 * </p>
 * <pre>
 *   D*D = (tN V + d) * (tN V + d)
 *       = tV (N tN) V + 2d tN V + d*d
 *       = tV A V + 2 tB V + c
 * </pre>
 * <p>
 * The quadric <code>Q=(A,B,c)=(N tN, dN, d*d)</code> is thus naturally
 * defined.  Addition of these quadrics have a simple form:
 * <code>Q1(V)+Q2(V)=(Q1+Q2)(V)</code> with
 * <code>Q1+Q2=(A1+A2, B1+B2, c1+c2)</code>
 * To compute the squared distance of a point to a set of planes, we can
 * then compute this quadric for each plane and sum each element of
 * these quadrics.  
 * </p>
 *
 * <p>
 * When an edge <code>(V1,V2)</code> is contracted into <code>V3</code>,
 * <code>Q1(V3)+Q2(V3)</code> represents the deviation to the set of
 * planes at <code>V1</code> and <code>V2</code>.  The cost of this
 * contraction is thus defined as <code>Q1(V3)+Q2(V3)</code>.
 * We want to minimize this error.  It can be shown that if <code>A</code>
 * is non singular, the optimal placement is for <code>V3=-inv(A) B</code>.
 * </p>
 *
 * <p>
 * The algorithm is straightforward:
 * </p>
 * <ol>
 *   <li>Quadrics are computed for all vertices.</li>
 *   <li>For each edge, compute the optimal placement and its cost.</li>
 *   <li>Loop on edges: starting with the lowest cost, each edge is processed
 *       until its cost is greater than the desired tolerance, and costs
 *       of adjacent edges are updated.</li>
 * </ol>
 *
 * <p>
 * The real implementation is slightly modified:
 * </p>
 * <ol type='a'>
 *   <li>Some checks must be performed to make sure that edge contraction does
 *       not modify the topology of the mesh.</li>
 *   <li>Optimal placement strategy can be chosen at run time among several
 *       choices.</li>
 *   <li>Boundary edges have to be preserved, otherwise they
 *       will shrink.  Virtual planes are added perpendicular to triangles at
 *       boundaries so that vertices can be decimated along those edges, but
 *       edges are stuck on their boundary.  Garland's thesis dissertation
 *       contains all informations about this process.</li>
 *   <li>Weights are added to compute quadrics, as described in Garland's
 *       dissertation.</li>
 *   <li>Edges are swapped after being contracted to improve triangle quality,
 *       as described by Frey in
 *       <a href="http://www.lis.inpg.fr/pages_perso/attali/DEA-IVR/PAPERS/frey00.ps">About Surface Remeshing</a>.</li>
 * </ol>
 */
public class DecimateHalfEdge
{
	private static Logger logger=Logger.getLogger(DecimateHalfEdge.class);
	private Mesh mesh;
	// 0.0 is not a valid value because it is a normalization factor.
	private double tolerance = 1.0;
	private int nrFinal = 0;
	private int placement;
	/**
	 * Optimal placement strategy, select the best vertex.
	 */
	public static final int POS_VERTEX = 0;
	/**
	 * Optimal placement strategy, contract an edge into its middle point.
	 */
	public static final int POS_MIDDLE = 1;
	/**
	 * Optimal placement strategy, the contracted point is on the edge.
	 */
	public static final int POS_EDGE = 2;
	/**
	 * Optimal placement strategy, the contracted point is the point which
	 * minimizes error metric.
	 */
	public static final int POS_OPTIMAL = 3;
	
	private class Quadric
	{
		public Metric3D A = new Metric3D();
		public double [] b = new double[3];
		public double c;
		public double area;
		public Quadric()
		{
			// By default, A is initialized to the identity matrix
			A.reset();
		}
		// Define a new quadrics by addition of 2 quadrics
		public Quadric(Quadric q1, Quadric q2)
		{
			assert q1.area > 0.0 : q1;
			assert q2.area > 0.0 : q2;
			double l1 = q1.area / (q1.area + q2.area);
			double l2 = q2.area / (q1.area + q2.area);
			for (int i = 0; i < 3; i++)
				for (int j = 0; j < 3; j++)
					A.data[i][j] = l1 * q1.A.data[i][j] + l2 * q2.A.data[i][j];
			for (int i = 0; i < 3; i++)
				b[i] = l1 * q1.b[i] + l2 * q2.b[i];
			c = l1 * q1.c + l2 * q2.c;
			area = q1.area + q2.area;
		}
		public double value(double [] vect)
		{
			double ret = c;
			ret += 2.0 * Matrix3D.prodSca(b, vect);
			for (int i = 0; i < b.length; i++)
				for (int j = 0; j < b.length; j++)
					ret += A.data[i][j] * vect[i] * vect[j];
			return ret;
		}
		public String toString()
		{
			return "A: "+A+"\n"+
			       " b: "+b[0]+" "+b[1]+" "+b[2]+"\n"+
			       " c: "+c;
		}
	}
	
	/**
	 * Creates a <code>DecimateHalfEdge</code> instance.
	 *
	 * @param m  the <code>Mesh</code> instance to refine.
	 * @param tol  maximal allowed error
	 */
	public DecimateHalfEdge(Mesh m, double tol)
	{
		mesh = m;
		tolerance = tol * tol;
		placement = POS_EDGE;
	}
	
	/**
	 * Creates a <code>DecimateHalfEdge</code> instance.
	 *
	 * @param m  the <code>Mesh</code> instance to refine.
	 * @param tol  maximal allowed error
	 * @param p  placement of the new point. Legitimate values are <code>POS_VERTEX</code> (at a vertex location), <code>POS_MIDDLE</code> (at the middle of the contracted edge), <code>POS_EDGE</code> (optimal placement on the contracted edge, this is the default) and <code>POS_OPTIMAL</code> (optimal placement).
	 */
	public DecimateHalfEdge(Mesh m, double tol, int p)
	{
		mesh = m;
		tolerance = tol * tol;
		placement = p;
	}
	
	/**
	 * Creates a <code>DecimateHalfEdge</code> instance.
	 *
	 * @param m  the <code>Mesh</code> instance to refine.
	 * @param n  the desired number of triangles
	 */
	public DecimateHalfEdge(Mesh m, int n)
	{
		mesh = m;
		nrFinal = n;
		placement = POS_EDGE;
	}
	
	/**
	 * Creates a <code>DecimateHalfEdge</code> instance.
	 *
	 * @param m  the <code>Mesh</code> instance to refine.
	 * @param n  the desired number of triangles
	 * @param p  placement of the new point. Legitimate values are <code>POS_VERTEX</code> (at a vertex location), <code>POS_MIDDLE</code> (at the middle of the contracted edge), <code>POS_EDGE</code> (optimal placement on the contracted edge, this is the default) and <code>POS_OPTIMAL</code> (optimal placement).
	 */
	public DecimateHalfEdge(Mesh m, int n, int p)
	{
		mesh = m;
		nrFinal = n;
		placement = p;
	}
	
	/**
	 * Contract all edges with the given error.
	 */
	public void compute()
	{
		mesh.buildEdges();
		logger.debug("Running DecimateHalfEdge");
		int roughNrNodes = mesh.getTriangles().size()/2;
		HashSet nodeset = new HashSet(roughNrNodes);
		HashMap quadricMap = new HashMap(roughNrNodes);
		int nrTriangles = 0;
		for (Iterator itf = mesh.getTriangles().iterator(); itf.hasNext(); )
		{
			Triangle f = (Triangle) itf.next();
			if (f.isOuter())
				continue;
			nrTriangles++;
			for (int i = 0; i < 3; i++)
			{
				Vertex n = f.vertex[i];
				if (nodeset.contains(n))
					continue;
				nodeset.add(n);
				quadricMap.put(n, new Quadric());
			}
		}
		// Compute quadrics
		PAVLSortedTree tree = new PAVLSortedTree();
		double [] b = new double[3];
		double [] vect1 = new double[3];
		double [] vect2 = new double[3];
		double [] normal = new double[3];
		for (Iterator itf = mesh.getTriangles().iterator(); itf.hasNext(); )
		{
			Triangle f = (Triangle) itf.next();
			if (f.isOuter())
				continue;
			double [] p0 = f.vertex[0].getUV();
			double [] p1 = f.vertex[1].getUV();
			double [] p2 = f.vertex[2].getUV();
			vect1[0] = p1[0] - p0[0];
			vect1[1] = p1[1] - p0[1];
			vect1[2] = p1[2] - p0[2];
			vect2[0] = p2[0] - p0[0];
			vect2[1] = p2[1] - p0[1];
			vect2[2] = p2[2] - p0[2];
			// This is in fact 2*area, but that does not matter
			Matrix3D.prodVect3D(vect1, vect2, normal);
			double norm = Matrix3D.norm(normal);
			double area = norm / tolerance;
			if (norm > 1.e-20)
			{
				for (int i = 0; i < 3; i++)
					normal[i] /=  norm;
			}
			double d = - Matrix3D.prodSca(normal, f.vertex[0].getUV());
			for (int i = 0; i < 3; i++)
			{
				Quadric q = (Quadric) quadricMap.get(f.vertex[i]);
				for (int k = 0; k < 3; k++)
				{
					q.b[k] += d * normal[k];
					for (int l = 0; l < 3; l++)
						q.A.data[k][l] += normal[k]*normal[l];
				}
				q.c += d*d;
				q.area += area;
			}
			// Penalty for boundary triangles
			HalfEdge e = f.edge;
			for (int i = 0; i < 3; i++)
			{
				e = e.next();
				if (e.hasAttributes(OTriangle.BOUNDARY))
				{
					//  Add a virtual plane
					//  In his dissertation, Garland suggests to
					//  add a weight proportional to squared edge
					//  length.
					//  length(vect2) == length(e)
					p0 = e.origin().getUV();
					p1 = e.destination().getUV();
					vect1[0] = p1[0] - p0[0];
					vect1[1] = p1[1] - p0[1];
					vect1[2] = p1[2] - p0[2];
					Matrix3D.prodVect3D(vect1, normal, vect2);
					for (int k = 0; k < 3; k++)
						vect2[k] *= 100.0;
					d = - Matrix3D.prodSca(vect2, e.origin().getUV());
					Quadric q1 = (Quadric) quadricMap.get(e.origin());
					Quadric q2 = (Quadric) quadricMap.get(e.destination());
					for (int k = 0; k < 3; k++)
					{
						q1.b[k] += d * vect2[k];
						q2.b[k] += d * vect2[k];
						for (int l = 0; l < 3; l++)
						{
							double delta = vect2[k]*vect2[l];
							q1.A.data[k][l] += delta;
							q2.A.data[k][l] += delta;
						}
					}
					q1.c += d*d;
					q2.c += d*d;
				}
			}
		}
		unmarkEdges();
		computeTree(tree, quadricMap);
		unmarkEdges();
		contractAllVertices(tree, nrTriangles, quadricMap);
	}
	
	private void unmarkEdges()
	{
		for (Iterator itf = mesh.getTriangles().iterator(); itf.hasNext(); )
		{
			Triangle f = (Triangle) itf.next();
			HalfEdge e = f.edge;
			for (int i = 0; i < 3; i++)
			{
				e = e.next();
				e.clearAttributes(OTriangle.MARKED);
			}
		}
	}
	
	private void computeTree(PAVLSortedTree tree, HashMap quadricMap)
	{
		//  Compute edge cost
		for (Iterator itf = mesh.getTriangles().iterator(); itf.hasNext(); )
		{
			Triangle f = (Triangle) itf.next();
			if (f.isOuter())
				continue;
			HalfEdge e = f.edge;
			for (int i = 0; i < 3; i++)
			{
				e = e.next();
				if (e.hasAttributes(OTriangle.MARKED))
					continue;
				e.setAttributes(OTriangle.MARKED);
				if (!e.hasAttributes(OTriangle.BOUNDARY))
				{
					HalfEdge sym = e.sym();
					sym.setAttributes(OTriangle.MARKED);
				}
				Vertex v1 = e.origin();
				Vertex v2 = e.destination();
				tree.insert(e.notOriented(), cost(v1, v2, quadricMap));
			}
		}
	}
	
	private boolean contractAllVertices(PAVLSortedTree tree, int nrTriangles, HashMap quadricMap)
	{
		OTriangle ot = new OTriangle();
		int contracted = 0;
		double [] temp = new double[3];
		boolean noSwap = true;
		int cnt = 0;
		while (tree.size() > 0 && nrTriangles > nrFinal)
		{
			HalfEdge edge = (HalfEdge) tree.first();
			double cost = -1.0;
			if (nrFinal == 0)
				cost = tree.getKey(edge);
			Vertex v1 = null, v2 = null, v3 = null;
			Quadric q1 = null, q2 = null, q3 = null;
			do {
				if (cost > tolerance)
					break;
				v1 = edge.origin();
				v2 = edge.destination();
				assert v1 != v2 : edge;
				/* FIXME: add an option so that boundary nodes may be frozen. */
				{
					q1 = (Quadric) quadricMap.get(v1);
					q2 = (Quadric) quadricMap.get(v2);
					assert q1 != null : v1;
					assert q2 != null : v2;
					q3 = new Quadric(q1, q2);
					v3 = optimalPlacement(v1, v2, q1, q2, q3, temp);
					edge.copyOTriangle(ot);
					if (ot.canContract(v3))
						break;
					if (logger.isDebugEnabled())
						logger.debug("Edge not contracted: "+edge);
				}
				edge = (HalfEdge) tree.next();
				if (nrFinal == 0)
					cost = tree.getKey(edge);
				else
					cost = -1.0;
			} while (edge != null && cost <= tolerance);
			if (cost > tolerance || edge == null)
				break;
			tree.remove(edge);
			if (logger.isDebugEnabled())
				logger.debug("Contract edge: "+edge+" into "+v3);
			Triangle t1 = edge.getTri();
			// Remove all edges of t1 and t2 from tree
			if (!t1.isOuter())
			{
				nrTriangles--;
				for (int i = 0; i < 3; i++)
				{
					edge = edge.next();
					tree.remove(edge.notOriented());
					assert !tree.containsValue(edge.notOriented());
				}
			}
			HalfEdge sym = edge.sym();
			Triangle t2 = sym.getTri();
			if (!t2.isOuter())
			{
				nrTriangles--;
				for (int i = 0; i < 3; i++)
				{
					sym = sym.next();
					tree.remove(sym.notOriented());
					assert !tree.containsValue(sym.notOriented());
				}
			}
			Vertex apex = edge.apex();
			// FIXME: is this test really necessary?
			if (apex == Vertex.outer)
				apex = sym.apex();
			//  Contract (v1,v2) into v3
			edge.contract(v3);
			contracted++;
			// Update edge costs
			quadricMap.remove(v1);
			quadricMap.remove(v2);
			quadricMap.put(v3, q3);
			edge = HalfEdge.find(v3, apex);
			assert edge != null : v3+" not connected to "+apex;
			assert edge.destination() == apex : ""+edge+"\n"+v3+"\n"+apex;
			do
			{
				edge = edge.nextOriginLoop();
				if (edge.destination() != Vertex.outer)
					tree.update(edge.notOriented(), cost(edge.destination(), v3, quadricMap));
			}
			while (edge.destination() != apex);
			if (noSwap)
				continue;
			
			edge = edge.next();
			assert edge.apex() == v3;
			edge.copyOTriangle(ot);
			// Check if edges can be swapped
			while(true)
			{
				if (ot.checkSwap3D(0.95) >= 0.0)
				{
					// Swap edge
					edge = HalfEdge.toHalfEdge(ot);
					for (int i = 0; i < 3; i++)
					{
						edge = edge.next();
						tree.remove(edge.notOriented());
						assert !tree.containsValue(edge.notOriented());
					}
					sym = edge.sym();
					for (int i = 0; i < 3; i++)
					{
						sym = sym.next();
						tree.remove(sym.notOriented());
						assert !tree.containsValue(sym.notOriented());
					}
					ot.swap();
					assert edge.destination() == ot.apex() : ot+" "+edge;
					for (int i = 0; i < 3; i++)
					{
						edge = edge.next();
						tree.insert(edge.notOriented(), cost(edge.origin(), edge.destination(), quadricMap));
					}
					edge = edge.next();
					assert edge.origin() == ot.apex() : ot+" "+edge;
					assert edge.destination() == ot.destination() : ot+" "+edge;
					edge = edge.sym();
					for (int i = 0; i < 2; i++)
					{
						edge = edge.next();
						tree.insert(edge.notOriented(), cost(edge.origin(), edge.destination(), quadricMap));
					}
				}
				else
				{
					edge = edge.nextApexLoop();
					if (edge.origin() == apex)
						break;
				}
			}
		}
		logger.info("Number of contracted edges: "+contracted);
		cnt = 0;
		HalfEdge edge = (HalfEdge) tree.first();
		while (edge != null)
		{
			if (tree.getKey(edge) > tolerance)
				break;
			cnt++;
			edge = (HalfEdge) tree.next();
		}
		logger.info("Number of edges which could have been contracted: "+cnt);
		logger.info("Number of other edges not contracted: "+(tree.size() - cnt));
		return contracted > 0;
	}
	
	private static double cost(Vertex v1, Vertex v2, HashMap quadricMap)
	{
		Quadric q1 = (Quadric) quadricMap.get(v1);
		assert q1 != null : v1;
		Quadric q2 = (Quadric) quadricMap.get(v2);
		assert q2 != null : v2;
		double ret = Math.min(
		  q1.value(v1.getUV()) + q2.value(v1.getUV()),
		  q1.value(v2.getUV()) + q2.value(v2.getUV()));
		assert ret >= -1.e-2 : q1+"\n"+q2+"\n"+ret;
		return ret;
	}
	
	private Vertex optimalPlacement(Vertex v1, Vertex v2, Quadric q1, Quadric q2, Quadric q3, double [] temp)
	{
		Vertex ret;
		assert v1 != Vertex.outer;
		assert v2 != Vertex.outer;
		/* FIXME: add an option so that boundary nodes may be frozen.
		if (!v1.isMutable())
		{
			assert v2.isMutable();
			ret = (Vertex) v1.clone();
			return ret;
		}
		else if (!v2.isMutable())
		{
			assert v1.isMutable();
			ret = (Vertex) v2.clone();
			return ret;
		}
		*/
		if (placement == POS_VERTEX)
		{
			if (q1.value(v2.getUV()) + q2.value(v2.getUV()) < q1.value(v1.getUV()) + q2.value(v1.getUV()))
				ret = (Vertex) v2.clone();
			else
				ret = (Vertex) v1.clone();
		}
		else if (placement == POS_MIDDLE)
		{
			// Keep a reference if there is one
			if (v1.getRef() != 0)
				ret = (Vertex) v1.clone();
			else
				ret = (Vertex) v2.clone();
			double [] p1 = v1.getUV();
			double [] p2 = v2.getUV();
			ret.moveTo(0.5*(p1[0]+p2[0]), 0.5*(p1[1]+p2[1]), 0.5*(p1[2]+p2[2]));
		}
		else
		{
			// POS_EDGE and POS_OPTIMAL
			// Keep a reference if there is one
			if (v1.getRef() != 0)
				ret = (Vertex) v1.clone();
			else
				ret = (Vertex) v2.clone();
			if (placement == POS_OPTIMAL)
			{
				Metric3D Qinv = q3.A.inv();
				if (Qinv != null)
				{
					double [] dx = Qinv.apply(q3.b);
					ret.moveTo(-dx[0], -dx[1], -dx[2]);
					return ret;
				}
			}
			// Find M = v1 + s(v2-v1) which minimizes
			//   q3(M) = s^2 (v2-v1)A(v2-v1) + s(v1A(v2-v1)+(v2-v1)Av1+2b(v2-v1))+cte
			//   q3'(M) = 2 s (v2-v1)A(v2-v1) + 2(v1A(v2-v1)+b(v2-v1))
			double [] p1 = v1.getUV();
			double [] p2 = v2.getUV();
			for (int i = 0; i < 3; i++)
				temp[i] = p2[i] - p1[i];
			double den = 0.0;
			double num = Matrix3D.prodSca(q3.b, temp);
			for (int i = 0; i < 3; i++)
			{
				for (int j = 0; j < 3; j++)
				{
					den += q3.A.data[i][j] * temp[i] * temp[j];
					num += q3.A.data[i][j] * temp[i] * p1[j];
				}
			}
			double s = 0.0;
			if (den > 1.0e-20 * Math.abs(num))
			{
				s = - num / den;
				if (s < 0.0)
					s = 0.0;
				else if (s > 1.0)
					s = 1.0;
				ret.moveTo(p1[0]+s*temp[0], p1[1]+s*temp[1], p1[2]+s*temp[2]);
			}
			else
			{
				if (q1.value(v2.getUV()) + q2.value(v2.getUV()) < q1.value(v1.getUV()) + q2.value(v1.getUV()))
					ret = (Vertex) v2.clone();
				else
					ret = (Vertex) v1.clone();
			}
		}
		return ret;
	}
	
	/**
	 * 
	 * @param args xmlDir, xmlFile, <-t telerance | -n triangle>, brepDir, brepFile
	 */
	public static void main(String[] args)
	{
		Mesh mesh=MeshReader.readObject3D(args[0], args[1], -1);
		if(args[2].equals("-n"))
			new DecimateHalfEdge(mesh, Integer.parseInt(args[2])).compute();
		else if(args[2].equals("-t"))
			new DecimateHalfEdge(mesh, Double.parseDouble(args[2])).compute();
		else
		{
			System.out.println("<xmlDir> <xmlFile> <-t telerance | -n triangle> <brepDir> <brepFile>");
			return;
		}
		MeshWriter.writeObject3D(mesh, args[0], args[1], args[3], args[4],1);
	}
}
