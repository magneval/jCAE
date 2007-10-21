/* jCAE stand for Java Computer Aided Engineering. Features are : Small CAD
   modeler, Finite element mesher, Plugin architecture.

    Copyright (C) 2005, by EADS CRC
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

package org.jcae.mesh.amibe.validation;

import org.jcae.mesh.amibe.ds.TriangleVH;
import org.jcae.mesh.amibe.ds.AbstractHalfEdge;
import org.jcae.mesh.amibe.ds.VirtualHalfEdge;
import org.jcae.mesh.amibe.metrics.Matrix3D;

/**
 * Compute angles between adjacent triangles.
 * This class implements the {@link QualityProcedure#quality(Object)}
 * method to compute angles between adjacent triangles.  The inner
 * products between the normal to the triangle and the normal to
 * adjacent triangles are computed, and the quality of the triangle
 * is set to the minimal value.  This is very useful to detect
 * inverted triangles in 3D on smooth surfaces.
 */
public class DihedralAngle extends QualityProcedure
{
	private VirtualHalfEdge ot = new VirtualHalfEdge();
	private VirtualHalfEdge sym = new VirtualHalfEdge();
	
	public DihedralAngle()
	{
		setType(QualityProcedure.FACE);
	}
	
	@Override
	public float quality(Object o)
	{
		if (!(o instanceof TriangleVH))
			throw new IllegalArgumentException();
		TriangleVH t = (TriangleVH) o;
		ot.bind(t);
		float ret = 1.0f;
		for (int i = 0; i < 3; i++)
		{
			ot.next();
			if (ot.hasAttributes(AbstractHalfEdge.BOUNDARY | AbstractHalfEdge.NONMANIFOLD))
				continue;
			if (ot.getAdj() == null)
				continue;
			sym.bind((TriangleVH) ot.getTri(), ot.getLocalNumber());
			sym.sym();
			if (t.getGroupId() != sym.getTri().getGroupId())
				continue;
			ot.computeNormal3D();
			double [] n1 = ot.getTempVector();
			sym.computeNormal3D();
			double [] n2 = sym.getTempVector();
			float dot = (float) Matrix3D.prodSca(n1, n2);
			if (dot < ret)
				ret = dot;
		}
		return ret;
	}
	
}
