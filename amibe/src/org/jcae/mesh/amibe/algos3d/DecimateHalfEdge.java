/* jCAE stand for Java Computer Aided Engineering. Features are : Small CAD
   modeler, Finite element mesher, Plugin architecture.

    Copyright (C) 2003,2006 by EADS CRC
    Copyright (C) 2007 by EADS France

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
import org.jcae.mesh.amibe.ds.AbstractTriangle;
import org.jcae.mesh.amibe.ds.Triangle;
import org.jcae.mesh.amibe.ds.Vertex;
import org.jcae.mesh.amibe.ds.AbstractHalfEdge;
import org.jcae.mesh.amibe.metrics.Quadric3DError;
import org.jcae.mesh.amibe.metrics.Matrix3D;
import org.jcae.mesh.xmldata.MeshReader;
import org.jcae.mesh.xmldata.MeshWriter;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.IOException;
import java.io.File;
import java.util.Map;
import java.util.HashMap;
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
public class DecimateHalfEdge extends AbstractAlgoHalfEdge
{
	private static Logger logger=Logger.getLogger(DecimateHalfEdge.class);
	private int placement = Quadric3DError.POS_EDGE;
	private HashMap<Vertex, Quadric3DError> quadricMap = null;
	private Vertex v1 = null, v2 = null;
	private Quadric3DError q1 = null, q2 = null;
	private Vertex v3;
	private final Vertex v4;
	private Quadric3DError q3 = new Quadric3DError();
	private final Quadric3DError q4 = new Quadric3DError();
	private static final boolean testDump = false;
	
	/**
	 * Creates a <code>DecimateHalfEdge</code> instance.
	 *
	 * @param m  the <code>Mesh</code> instance to refine.
	 * @param options  map containing key-value pairs to modify algorithm
	 *        behaviour.  Valid keys are <code>size</code>,
	 *        <code>placement</code> and <code>maxtriangles</code>.
	 */
	public DecimateHalfEdge(final Mesh m, final Map<String, String> options)
	{
		super(m);
		v3 = (Vertex) m.factory.createVertex(0.0, 0.0, 0.0);
		v4 = (Vertex) m.factory.createVertex(0.0, 0.0, 0.0);
		for (final Map.Entry<String, String> opt: options.entrySet())
		{
			final String key = opt.getKey();
			final String val = opt.getValue();
			if (key.equals("size"))
			{
				final double sizeTarget = new Double(val).doubleValue();
				tolerance = sizeTarget * sizeTarget;
				logger.debug("Tolerance: "+tolerance);
			}
			else if (key.equals("placement"))
			{
				placement = Integer.valueOf(val).intValue();
				logger.debug("Placement: "+placement);
			}
			else if (key.equals("maxtriangles"))
			{
				nrFinal = Integer.valueOf(val).intValue();
				logger.debug("Nr max triangles: "+nrFinal);
			}
			else
				throw new RuntimeException("Unknown option: "+key);
		}
	}
	
	@Override
	public Logger thisLogger()
	{
		return logger;
	}

	@Override
	public void preProcessAllHalfEdges()
	{
		final int roughNrNodes = mesh.getTriangles().size()/2;
		quadricMap = new HashMap<Vertex, Quadric3DError>(roughNrNodes);
		for (AbstractTriangle af: mesh.getTriangles())
		{
			if (!af.isWritable())
				continue;
			for (int i = 0; i < 3; i++)
			{
				final Vertex n = af.vertex[i];
				if (!quadricMap.containsKey(n))
					quadricMap.put(n, new Quadric3DError());
			}
		}
		// Compute quadrics
		final double [] vect1 = new double[3];
		final double [] vect2 = new double[3];
		final double [] normal = new double[3];
		for (AbstractTriangle af: mesh.getTriangles())
		{
			if (!af.isWritable())
				continue;
			final Triangle f = (Triangle) af;
			double [] p0 = f.vertex[0].getUV();
			double [] p1 = f.vertex[1].getUV();
			final double [] p2 = f.vertex[2].getUV();
			vect1[0] = p1[0] - p0[0];
			vect1[1] = p1[1] - p0[1];
			vect1[2] = p1[2] - p0[2];
			vect2[0] = p2[0] - p0[0];
			vect2[1] = p2[1] - p0[1];
			vect2[2] = p2[2] - p0[2];
			// This is in fact 2*area, but that does not matter
			Matrix3D.prodVect3D(vect1, vect2, normal);
			final double norm = Matrix3D.norm(normal);
			double area = norm;
			if (tolerance > 0.0)
				area /= tolerance;
			if (norm > 1.e-20)
			{
				for (int i = 0; i < 3; i++)
					normal[i] /=  norm;
			}
			double d = - Matrix3D.prodSca(normal, f.vertex[0].getUV());
			for (int i = 0; i < 3; i++)
			{
				final Quadric3DError q = quadricMap.get(f.vertex[i]);
				q.addError(normal, d, area);
			}
			// Penalty for boundary triangles
			HalfEdge e = (HalfEdge) f.getAbstractHalfEdge();
			for (int i = 0; i < 3; i++)
			{
				e = (HalfEdge) e.next();
				if (e.hasAttributes(AbstractHalfEdge.BOUNDARY) || e.hasAttributes(AbstractHalfEdge.NONMANIFOLD))
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
					final Quadric3DError q1 = quadricMap.get(e.origin());
					final Quadric3DError q2 = quadricMap.get(e.destination());
					//area = Matrix3D.norm(vect2) / tolerance;
					area = 0.0;
					q1.addError(vect2, d, area);
					q2.addError(vect2, d, area);
				}
			}
		}
	}

	@Override
	protected void postComputeTree()
	{
		if (testDump)
			restoreState();
	}

	@Override
	protected void appendDumpState(final ObjectOutputStream out)
		throws IOException
	{
		out.writeObject(quadricMap);
	}

	@Override
	protected void appendRestoreState(final ObjectInputStream q)
		throws IOException
	{
		try
		{
			quadricMap = (HashMap<Vertex, Quadric3DError>) q.readObject();
		}
		catch (final ClassNotFoundException ex)
		{
			ex.printStackTrace();
			System.exit(-1);
		}
	}

	@Override
	public double cost(final HalfEdge e)
	{
		final Vertex o = e.origin();
		final Vertex d = e.destination();
		final Quadric3DError q1 = quadricMap.get(o);
		assert q1 != null : o;
		final Quadric3DError q2 = quadricMap.get(d);
		assert q2 != null : d;
		q4.computeQuadric3DError(q1, q2);
		q4.optimalPlacement(o, d, q1, q2, placement, v4);
		final double ret = q1.value(v4.getUV()) + q2.value(v4.getUV());
		// TODO: check why this assertion sometimes fail
		// assert ret >= -1.e-2 : q1+"\n"+q2+"\n"+ret;
		return ret;
	}

	@Override
	public boolean canProcessEdge(final HalfEdge current)
	{
		v1 = current.origin();
		v2 = current.destination();
		assert v1 != v2 : current;
		// If an endpoint is not writable, its neighborhood is
		// not fully determined and contraction must not be
		// performed.
		if (!v1.isWritable() || !v2.isWritable())
			return false;
		/* FIXME: add an option so that boundary nodes may be frozen. */
		q1 = quadricMap.get(v1);
		q2 = quadricMap.get(v2);
		assert q1 != null : current;
		assert q2 != null : current;
		q3.computeQuadric3DError(q1, q2);
		q3.optimalPlacement(v1, v2, q1, q2, placement, v3);
		// For now, do not contract non manifold edges
		return (!current.hasAttributes(AbstractHalfEdge.NONMANIFOLD) && current.canCollapse(v3));
	}

	@Override
	public void preProcessEdge()
	{
		if (testDump)
			dumpState();
		if (v1 != null)
		{
			// v1 and v2 have been removed from the mesh,
			// they can be reused.
			v3 = v1;
			q3 = q1;
		}
	}

	@Override
	public HalfEdge processEdge(HalfEdge current)
	{
		if (logger.isDebugEnabled())
			logger.debug("Contract edge: "+current+" into "+v3);
		final Triangle t1 = current.getTri();
		// HalfEdge instances on t1 and t2 will be deleted
		// when edge is contracted, and we do not know whether
		// they appear within tree or their symmetric ones,
		// so remove them now.
		if (!tree.remove(current.notOriented()))
			notInTree++;
		if (t1.isWritable())
		{
			nrTriangles--;
			for (int i = 0; i < 2; i++)
			{
				current = (HalfEdge) current.next();
				if (!tree.remove(current.notOriented()))
					notInTree++;
				assert !tree.contains(current.notOriented());
			}
			current = (HalfEdge) current.next();
		}
		HalfEdge sym = (HalfEdge) current.sym();
		final Triangle t2 = sym.getTri();
		if (t2.isWritable())
		{
			nrTriangles--;
			for (int i = 0; i < 2; i++)
			{
				sym = (HalfEdge) sym.next();
				if (!tree.remove(sym.notOriented()))
					notInTree++;
				assert !tree.contains(sym.notOriented());
			}
			sym = (HalfEdge) sym.next();
		}
		//  Contract (v1,v2) into v3
		//  By convention, collapse() returns edge (v3, apex)
		if (current.hasAttributes(AbstractHalfEdge.OUTER))
			current = (HalfEdge) current.sym();
		final Vertex apex = current.apex();
		current = (HalfEdge) current.collapse(mesh, v3);
		quadricMap.remove(v1);
		quadricMap.remove(v2);
		// Update edge costs
		quadricMap.put(v3, q3);
		assert current != null : v3+" not connected to "+apex;
		assert current.origin() == v3 : ""+current+"\n"+v3+"\n"+apex;
		assert current.destination() == apex : ""+current+"\n"+v3+"\n"+apex;
		do
		{
			current = (HalfEdge) current.nextOriginLoop();
			if (current.destination() != mesh.outerVertex && current.destination().isReadable() && current.origin().isReadable())
				tree.update(current.notOriented(), cost(current));
		}
		while (current.destination() != apex);
		return (HalfEdge) current.next();
	}
	
	@Override
	public void postProcessAllHalfEdges()
	{
		logger.info("Number of contracted edges: "+processed);
		logger.info("Total number of edges not contracted during processing: "+notProcessed);
		logger.info("Total number of edges swapped to increase quality: "+swapped);
		//logger.info("Number of edges which were not in the binary tree before being removed: "+notInTree);
		logger.info("Number of edges still present in the binary tree: "+tree.size());
	}

	private final static String usageString = "<xmlDir> <-t tolerance | -n nrTriangles> <brepFile> <outputDir>";

	/**
	 * 
	 * @param args xmlDir, -t tolerance | -n triangle, brepFile, output
	 */
	public static void main(final String[] args)
	{
		final HashMap<String, String> options = new HashMap<String, String>();
		if(args.length != 5)
		{
			System.out.println(usageString);
			return;
		}
		if(args[1].equals("-n"))
			options.put("maxtriangles", args[2]);
		else if(args[1].equals("-t"))
			options.put("size", args[2]);
		else
		{
			System.out.println(usageString);
			return;
		}
		logger.info("Load geometry file");
		final org.jcae.mesh.amibe.traits.TriangleTraitsBuilder ttb = new org.jcae.mesh.amibe.traits.TriangleTraitsBuilder();
		ttb.addHalfEdge();
		final org.jcae.mesh.amibe.traits.MeshTraitsBuilder mtb = new org.jcae.mesh.amibe.traits.MeshTraitsBuilder();
		mtb.addTriangleSet();
		mtb.add(ttb);
		final Mesh mesh = new Mesh(mtb);
		MeshReader.readObject3D(mesh, args[0], "jcae3d", -1);
		new DecimateHalfEdge(mesh, options).compute();
		final File brepFile=new File(args[3]);
		MeshWriter.writeObject3D(mesh, args[4], "jcae3d", brepFile.getParent(), brepFile.getName());
	}
}
