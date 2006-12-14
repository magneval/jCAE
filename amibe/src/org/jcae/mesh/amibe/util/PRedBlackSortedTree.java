/* jCAE stand for Java Computer Aided Engineering. Features are : Small CAD
   modeler, Finite element mesher, Plugin architecture.

    Copyright (C) 2006 by EADS CRC

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

package org.jcae.mesh.amibe.util;

import java.util.HashMap;
import java.util.Iterator;
import java.io.Serializable;
import org.apache.log4j.Logger;

/**
 * Red-black binary trees to store quality factors.
 * Main ideas come from Ben Pfaff's <a href="http://adtinfo.org/">GNU libavl</a>.
 * These trees are used to sort vertices, edges, or triangles according
 * to their quality factors, and to process them in increasing or decreasing
 * order after they have been sorted.  See examples in algorithms from
 * {@link org.jcae.mesh.amibe.algos3d}.
 * A red-black tree has the following properties:
 * <ol>
 * <li>Null nodes are black.</li>
 * <li>A red node has no red child.</li>
 * <li>Paths from any node to external leaves contain the same number of black
 *     nodes.</li>
 * </ol>
 * By convention, root node is always black in order to simplify node insertion
 * and removal.  Node insertions and removals are explained in detail at
 * <a href="http://en.wikipedia.org/wiki/Red-Black_tree">wikipedia</a>.
 */
public class PRedBlackSortedTree extends QSortedTree
{
	private static Logger logger = Logger.getLogger(PRedBlackSortedTree.class);	
	private static class Node extends QSortedTreeNode
	{
		private boolean isRed;
		
		public QSortedTreeNode [] newChilds()
		{
			return new Node[2];
		}

		public Node(Object o, double v)
		{
			super(o, v);
			isRed = true;
		}
		
		public void reset(double v)
		{
			super.reset(v);
			isRed = true;
		}

		public String toString()
		{
			return "Key: "+value+" "+(isRed ? "red" : "black");
		}
	}

	public final QSortedTreeNode newNode(Object o, double v)
	{
		return new Node(o, v);
	}

	// Helper function
	private static final boolean isRedNode(QSortedTreeNode x)
	{
		return (x != null) && ((Node) x).isRed;
	}
	
	public final void insertNode(QSortedTreeNode o)
	{
		Node node = (Node) o;
		Node current = (Node) root.child[0];
		Node parent = (Node) root;
		int lastDir = 0;
		while (current != null)
		{
			if (current.value > node.value)
				lastDir = 0;
			else
				lastDir = 1;
			parent = current;
			current = (Node) current.child[lastDir];
		}
		// Insert node
		parent.child[lastDir] = node;
		node.parent = parent;
		// Node color is red, so property 3 is preserved.
		// We must check if property 2 is violated, in which
		// case our tree has to be rebalanced and/or repainted.
		// Case 1: root node
		if (parent == root)
		{
			// We enforce root node to be black, this eases
			// other cases below.
			logger.debug("Case 1");
			node.isRed = false;
			return;
		}
		for (current = node; current != root; )
		{
			parent = (Node) current.parent;
			// If parent is black, property 2 is preserved,
			// everything is fine.
			// Case 2: parent is black
			if (parent == root || !parent.isRed)
			{
				logger.debug("Case 2");
				break;
			}
			// Parent is red, so it cannot be the root tree,
			// and grandparent is black.
			Node grandparent = (Node) parent.parent;
			assert grandparent != root;
			if (grandparent.child[0] == parent)
				lastDir = 0;
			else
				lastDir = 1;
			int sibDir = 1 - lastDir;
			Node uncle = (Node) grandparent.child[sibDir];
			if (isRedNode(uncle))
			{
				// Case 3: uncle is red
				/* Paint nodes and continue from grandparent
				     gB                gR
				    / \   ------>     / \
				   pR uR            pB  uB
				    \                 \
				    cR                cR
				*/
				logger.debug("Case 3");
				parent.isRed = false;
				uncle.isRed = false;
				grandparent.isRed = true;
				current = grandparent;
			}
			else
			{
				assert !isRedNode(uncle);
				if (parent.child[lastDir] != current)
				{
					/* Rotate to put red nodes on the
					   same side
					     gB                gB
					    / \   ------>     / \
					   pR uB            cR  uB
					    \               /
					    cR             pR
					*/
					logger.debug("Case 4");
					current = parent;
					if (lastDir == 0)
						grandparent.child[0] = parent.rotateL();
					else
						grandparent.child[1] = parent.rotateR();
					parent = (Node) current.parent;
				}
				/* Rotate on opposite way and recolor.  Either
				   uncle is null, or we come from case 3 and
				   current node has 2 black children.
				        gB                pB
				       / \   ------>     /  \
				      pR uB            cR   gR
				     / \              / \   / \
				    cR zB            xB yB zB uB
				   / \
				  xB yB
				*/
				assert (uncle == null && parent.child[sibDir] == null &&
				  current.child[0] == null && current.child[1] == null) ||
				 (uncle != null && parent.child[sibDir] != null &&
				  current.child[0] != null && current.child[1] != null);
				logger.debug("Case 5");

				Node greatgrandparent = (Node) grandparent.parent;
				grandparent.isRed = true;
				parent.isRed = false;
				if (greatgrandparent.child[0] == grandparent)
					lastDir = 0;
				else
					lastDir = 1;
				// lastDir has been modified, so use sibDir here
				if (sibDir == 1)
					greatgrandparent.child[lastDir] = grandparent.rotateR();
				else
					greatgrandparent.child[lastDir] = grandparent.rotateL();
				break;
			}
		}
		((Node) root.child[0]).isRed = false;
		assert isValid();
	}
	
	public final double removeNode(QSortedTreeNode o)
	{
		Node p = (Node) o;
		double ret = p.value;
		Node q = (Node) p.parent;
		int lastDir = 0;
		if (q.child[1] == p)
			lastDir = 1;
		if (p.child[1] == null)
		{
			q.child[lastDir] = p.child[0];
			if (q.child[lastDir] != null)
				q.child[lastDir].parent = q;
		}
		else if (p.child[0] == null)
		{
			q.child[lastDir] = p.child[1];
			if (q.child[lastDir] != null)
				q.child[lastDir].parent = q;
		}
		else
		{
			// Swap p with its successor, and delete it
			Node r = (Node) p.nextNode();
			// Do not modify p's color!
			copyNode(r, p);
			p = r;
			q = (Node) p.parent;
			if (q.child[0] == p)
				lastDir = 0;
			else
				lastDir = 1;
			assert p.child[0] == null;
			q.child[lastDir] = p.child[1];
			if (q.child[lastDir] != null)
				q.child[lastDir].parent = q;
		}
		if (p.isRed)
			return ret;
		for (;;)
		{
			p = (Node) q.child[lastDir];
			if (isRedNode(p))
			{
				p.isRed = false;
				logger.debug("Red node :-)");
				break;
			}
			// Case 1: root tree
			if (q == root || q.child[0] == root)
			{
				logger.debug("Case 1");
				break;
			}
			int sibDir = 1 - lastDir;
			Node grandparent = (Node) q.parent;
			int gLastDir = 0;
			if (grandparent.child[1] == q)
				gLastDir = 1;
			Node sibling = (Node) q.child[sibDir];
			if (sibling.isRed)
			{
				// Case 2: sibling is red
				logger.debug("Case 2");
				sibling.isRed = false;
				q.isRed = true;

				/* Example with lastDir == 0
				     qB          qR             sB => gB
				     / \   ----> / \    ---->   / \
				    pB sR       pB sB          qR  bB
				       / \         / \        / \
				      aB bB       aB bB      pB aB => sB
				*/
				if (lastDir == 0)
					grandparent.child[gLastDir] = q.rotateL();
				else
					grandparent.child[gLastDir] = q.rotateR();
				grandparent = sibling;
				sibling = (Node) q.child[sibDir];
				if (grandparent.child[0] == q)
					gLastDir = 1;
				else
					gLastDir = 1;
			}
			if (!q.isRed && !sibling.isRed && !isRedNode(sibling.child[0]) && !isRedNode(sibling.child[1]))
			{
				// Case 3: parent, sibling and sibling's
				// children are black
				logger.debug("Case 3");
				sibling.isRed = true;
			}
			else
			{
				assert !isRedNode(sibling);
				// Now sibling is black.
				if (!isRedNode(sibling.child[0]) && !isRedNode(sibling.child[1]))
				{
					// Case 4: sibling and sibling's
					// children are black, but parent is
					// red.
					assert q.isRed;
					logger.debug("Case 4");
					sibling.isRed = true;
					q.isRed = false;
					break;
				}
				else
				{
					if (isRedNode(sibling.child[lastDir]) && !isRedNode(sibling.child[sibDir]))
					{
						// Case 5: sibling is black, left child is
						// red and right child is black.
						// Rotate at sibling and paint nodes
						// so that sibling.child[sibDir] is red.
						logger.debug("Case 5");
						Node y = (Node) sibling.child[lastDir];
						y.isRed = false;
						sibling.isRed = true;
						if (lastDir == 0)
							q.child[sibDir] = sibling.rotateR();
						else
							q.child[sibDir] = sibling.rotateL();
						sibling = y;
						/* Example with lastDir == 0
						  qR       qR       qB
						  / \ ---> / \ ---> / \
						 xB sB    xB yR    xB yB
						   / \      / \      / \
						  yR  zB   T1  sB   T1  sR
						 / \          / \      / \
						T1 T2        T2  zB   T2  zB
						*/
					}
					logger.debug("Case 6");
					// Case 6: sibling is black and its right child
					// is red.
					/*
					  q*            qB             s*      
					 / \    --->   / \    --->    / \      
					xB sB         xB s*         qB   yB     
					  / \           / \        / \   / \     
					 T1  yR        T1  yB     xB T1 T2 zB    
					    / \           / \
					   T2  zB        T2  zB
					*/
					assert !isRedNode(sibling) && isRedNode(sibling.child[sibDir]);
					sibling.isRed = q.isRed;
					q.isRed = false;
					((Node) sibling.child[sibDir]).isRed = false;
					if (lastDir == 0)
						grandparent.child[gLastDir] = q.rotateL();
					else
						grandparent.child[gLastDir] = q.rotateR();
					break;
				}
			}
			if (q.parent.child[0] == q)
				lastDir = 0;
			else
				lastDir = 1;
			q = (Node) q.parent;
		}
		assert isValid();
		return ret;
	}
	
	private boolean isValid()
	{
		// Call debugIsValid() only when debugging, otherwise
		// tree manipulations are way too slow.
		return true;
	}

	private boolean debugIsValid()
	{
		Node current = (Node) root.child[0];
		if (isRedNode(current))
			return false;
		if (current == null)
			return true;
		int blackNodes = 0;
		int seenRoot = 0;
		while (current.child[0] != null)
		{
			if (!isRedNode(current))
				blackNodes++;
			current = (Node) current.child[0];
		}
		// Now traverse the tree
		while (current != root)
		{
			if (!isRedNode(current))
				blackNodes--;
			else if (isRedNode(current.child[0]) || isRedNode(current.child[1]))
				return false;
			if (current.child[0] != null && current.child[0].value > current.value)
				return false;
			if (current.child[1] != null && current.child[1].value < current.value)
				return false;
			if (current.child[1] != null)
			{
				current = (Node) current.child[1];
				if (!isRedNode(current))
					blackNodes++;
				else if (isRedNode(current.child[0]) || isRedNode(current.child[1]))
					return false;
				while (current.child[0] != null)
				{
					current = (Node) current.child[0];
					if (!isRedNode(current))
						blackNodes++;
					else if (isRedNode(current.child[0]) || isRedNode(current.child[1]))
						return false;
				}
			}
			else
			{
				// Walk upwards
				while (current.parent.child[0] != current)
				{
					if (!isRedNode(current))
						blackNodes--;
					else if (isRedNode(current.child[0]) || isRedNode(current.child[1]))
						return false;
					current = (Node) current.parent;
				}
				current = (Node) current.parent;
			}
		}
		return true;
	}

	private static Integer [] unitTestInit(QSortedTree tree, int n)
	{
		assert tree.isEmpty();
		Integer [] ret = new Integer[n];
		for (int i = 0; i < ret.length; i++)
			ret[i] = new Integer(i);
		for (int i = 0; i < ret.length; i++)
			tree.insert(ret[i], (double) i);
		return ret;
	}
	
	private static void unitTest1(QSortedTree tree, int n)
	{
		// Remove in ascending order
		Integer [] iii = unitTestInit(tree, n);
		for (int i = iii.length - 1; i >= 0; i--)
			tree.remove(iii[i]);
		if (!tree.isEmpty())
			throw new RuntimeException();
	}
	
	private static void unitTest2(QSortedTree tree, int n)
	{
		// Remove in ascending order
		Integer [] iii = unitTestInit(tree, n);
		for (int i = 0; i < iii.length; i++)
			tree.remove(iii[i]);
		if (!tree.isEmpty())
			throw new RuntimeException();
	}
	
	private static void unitTest3(QSortedTree tree, int n)
	{
		Integer [] iii = unitTestInit(tree, n);
		for (int i = 0; i < iii.length / 2; i++)
		{
			tree.remove(iii[i]);
			tree.remove(iii[i+iii.length / 2]);
		}
		if (!tree.isEmpty())
			throw new RuntimeException();
	}
	
	private static void unitTest4(QSortedTree tree, int n)
	{
		// Remove in ascending order
		Integer [] iii = unitTestInit(tree, n);
		for (int i = 0; i < iii.length / 2; i++)
		{
			tree.remove(iii[iii.length / 2+i]);
			tree.remove(iii[iii.length / 2-1-i]);
		}
		if (!tree.isEmpty())
			throw new RuntimeException();
	}
	
	public static void main(String args[])
	{
		PRedBlackSortedTree tree = new PRedBlackSortedTree();
		// Check with various lengths
		for (int n = 10; n < 1000; n+=2)
		{
			try
			{
				unitTest1(tree, n);
				unitTest2(tree, n);
				unitTest3(tree, n);
				unitTest4(tree, n);
			}
			catch (Exception ex)
			{
				System.out.println("Failed with length "+n);
				ex.printStackTrace();
				System.exit(1);
			}
		}
		System.out.println("ok");
		//tree = new PRedBlackSortedTree();
		//for (int i = 9; i >= 0; i--)
		//	tree.insert(iii[i], (double) i);
		//tree.remove(iii[0]);
		//tree.remove(iii[8]);
		//tree.remove(iii[9]);
		//tree.showValues();
	}
		
}