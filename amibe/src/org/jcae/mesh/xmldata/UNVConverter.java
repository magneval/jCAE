/*
 * Project Info:  http://jcae.sourceforge.net
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation, Inc.,
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA.
 *
 * (C) Copyright 2004, by EADS CRC
 */

package org.jcae.mesh.xmldata;

import gnu.trove.TIntArrayList;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIntHashMap;
import java.io.*;
import java.lang.reflect.Method;
import java.nio.DoubleBuffer;
import java.nio.IntBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.security.AccessController;
import java.security.PrivilegedAction;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Extract groups from the full mesh and write them to a UNV file.
 * It renumber elements so there ids are from 1 to n. Although it
 * uses the NIO it may not be performant as efficient as a full
 * dump of the mesh to UNV.
 * @author Jerome Robert
 *
 */
public class UNVConverter
{
	private final static String cr=System.getProperty("line.separator");
	
	/** workaround for Bug ID4724038, see
	 * http://bugs.sun.com/bugdatabase/view_bug.do;:YfiG?bug_id=4724038 
	 */
	public static void clean(final MappedByteBuffer buffer)
	{
		AccessController.doPrivileged(new PrivilegedAction()
        {
            public Object run()
            {
            	try
				{
            		Method getCleanerMethod = buffer.getClass().getMethod("cleaner", new Class[0]);
            		getCleanerMethod.setAccessible(true);
            		sun.misc.Cleaner cleaner = (sun.misc.Cleaner)getCleanerMethod.invoke(buffer,new Object[0]);
            		if(cleaner!=null)
            			cleaner.clean();
				}
            	catch(Exception e)
				{
            		e.printStackTrace();
				}
            	return null;
            }
        });
	}	
	
	/**
	 * A main method for debugging
	 * @param args
	 */
	public static void main(String[] args)
	{
	
		try
		{
			new UNVConverter(new File("/home/jerome/Project/mesh33035.brep.jcae"), new int[]{0}).
				writeUNV(new PrintStream(new FileOutputStream(new File("/tmp/blub.unv"))));	
			//	writeUNV(System.out);
			
			
		} catch (ParserConfigurationException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SAXException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	private File directory;
	private Document document;
	private int[] groupIds;
	private int[][] groups;
	private String[] names;
	private int numberOfTriangles;
	/**
	 * 
	 */
	public UNVConverter(File directory, int[] groupIds)
	{
		this.directory=directory;
		this.groupIds=groupIds;		
	}

	private File getNodeFile()
	{
		Element xmlNodes = (Element) document.getElementsByTagName(
			"nodes").item(0);
		String a=((Element)xmlNodes.getElementsByTagName("file").item(0)).getAttribute("location");
		return new File(directory, a);
	}
	
	private File getTriaFile()
	{
		Element xmlNodes = (Element) document.getElementsByTagName(
			"triangles").item(0);
		Node fn = xmlNodes.getElementsByTagName("file").item(0);
		String a=((Element)fn).getAttribute("location");
		return new File(directory, a);
	}
	
	/**
	 * @param the xml element of DOM tree corresponding to the tag "groups".
	 * @param a group.
	 * @return the xml element of DOM tree corresponding to the group.
	 */
	private Element getXmlGroup(Element xmlGroups, int id)
	{
		NodeList list = xmlGroups.getElementsByTagName("group");
		Element elt = null;
		int i = 0;
		boolean found = false;
		while (!found && i < list.getLength())
		{
			elt = (Element) list.item(i);
			int aId = -1;
			try
			{
				aId = Integer.parseInt(elt.getAttribute("id"));
			} catch (Exception e)
			{
				e.printStackTrace(System.out);
			}
			if (id == aId)
			{
				found = true;
			} else
			{
				i++;
			}
		}
		if (found)
		{
			return elt;
		} else
		{
			return null;
		}
	}	
	
	private void readGroups()
	{
		Element xmlGroups=(Element) document.getElementsByTagName("groups").item(0);
		groups=new int[groupIds.length][];
		numberOfTriangles=0;
		names=new String[groupIds.length];
		for(int i=0; i<groupIds.length; i++)
		{
			Element e=getXmlGroup(xmlGroups, groupIds[i]);
			
			Element nameNode=(Element)e.getElementsByTagName("name").item(0);
			names[i]=nameNode.getChildNodes().item(0).getNodeValue();			
			
			Element numberNode=(Element)e.getElementsByTagName("number").item(0);
			String v=numberNode.getChildNodes().item(0).getNodeValue();
			int number=Integer.parseInt(v);
			groups[i]=new int[number];
			numberOfTriangles+=number;
			if(number==0) continue;
				
				
			String groupFileN=((Element)e.getElementsByTagName("file").item(0)).getAttribute("location");
			String os=((Element)e.getElementsByTagName("file").item(0)).getAttribute("offset");
			File groupFile=new File(directory, groupFileN);		
			long offset=Long.parseLong(os);
			
			try
			{
				// Open the file and then get a channel from the stream
		        FileInputStream fisG = new FileInputStream(groupFile);
		        FileChannel fcG = fisG.getChannel();
		 
		        // Get the file's size and then map it into memory
		        MappedByteBuffer bbG = fcG.map(FileChannel.MapMode.READ_ONLY, offset*4, number*4);		
				IntBuffer groupsBuffer = bbG.asIntBuffer();
				
				groupsBuffer.get(groups[i]);
				fcG.close();
				fisG.close();
				clean(bbG);
			} catch(IOException ex)
			{
				ex.printStackTrace();
			}
		}				
	}
	
	private int[] readTriangles() throws IOException
	{				
		File f=getTriaFile();
		// Open the file and then get a channel from the stream
        FileInputStream fis = new FileInputStream(f);
        FileChannel fc = fis.getChannel();
 
        // Get the file's size and then map it into memory
        MappedByteBuffer bb = fc.map(FileChannel.MapMode.READ_ONLY, 0, f.length());
        IntBuffer trias=bb.asIntBuffer();
        
        int[] ns=new int[3];
        int[] toReturn=new int[numberOfTriangles*3];
        int count=0;
        for(int i=0; i<groups.length; i++)
        {
        	for(int j=0; j<groups[i].length; j++)
        	{
        		trias.position(groups[i][j]*3);
        		trias.get(toReturn, count, 3);
        		count+=3;
        	}
        }
        return toReturn;        
	}

	/** Add spaces before a string to make it 10 characters */
	private String spaces(String s)
	{
		int n = 10 - s.length();
		char[] c = new char[n];
		for (int i=0; i<n; i++)
			c[i]=' ';
		return (new String(c))+s;
	}	
	
	public void writeUNV(PrintStream out) throws ParserConfigurationException, SAXException, IOException
	{
		document=XMLHelper.parseXML(new File(directory,"jcae3d"));
		readGroups();
		int[] triangle=readTriangles();
		TIntIntHashMap amibeNodeToUNVNode=new TIntIntHashMap();		
		writeUNVNodes(out, new TIntHashSet(triangle).toArray(), amibeNodeToUNVNode);
		/*System.out.println(new TIntArrayList(triangle).toString());
		System.out.println(new TIntArrayList(amibeNodeToUNVNode.keys()).toString());
		System.out.println(new TIntArrayList(amibeNodeToUNVNode.getValues()).toString());*/
		TIntIntHashMap amibeTriaToUNVTria=new TIntIntHashMap();
		writeUNVTriangles(out, triangle, amibeNodeToUNVNode, amibeTriaToUNVTria);
		triangle=null;
		amibeNodeToUNVNode=null;
		writeUNVGroups(out, amibeTriaToUNVTria);
	}
	
	/**
	 * @param out
	 * @param amibeTriaToUNVTria
	 */
	private void writeUNVGroups(PrintStream out, TIntIntHashMap amibeTriaToUNVTria)
	{
		out.println("    -1"+cr+"  2430");
		int count =  0;
		for(int i=0;i<groups.length; i++)
		{			
			count++;
			out.println("1      0         0         0         0         0         0      "+groups[i].length);
			out.println(names[i]);
			int countg=0;
			for(int j=0; j<groups[i].length; j++)
			{				
				out.print("         8"+spaces(""+amibeTriaToUNVTria.get(groups[i][j])));
				countg++;
				if ((countg % 4) == 0)
					out.println("");
			}
			if ((countg % 4) !=0 )
				out.println("");
		}
		out.println("    -1");

	}

	private void writeUNVNodes(PrintStream out, int[] nodesID, TIntIntHashMap amibeToUNV) throws IOException
	{
		File f=getNodeFile();
		// Open the file and then get a channel from the stream
        FileInputStream fis = new FileInputStream(f);
        FileChannel fc = fis.getChannel();
 
        // Get the file's size and then map it into memory
        int sz = (int)fc.size();
        MappedByteBuffer bb = fc.map(FileChannel.MapMode.READ_ONLY, 0, f.length());
        DoubleBuffer nodesBuffer=bb.asDoubleBuffer();        
		
		out.println("    -1"+cr+"  2411");
		int count =  0;
		double x,y,z;
		for(int i=0; i<nodesID.length; i++)
		{
			int iid=nodesID[i]*3;
			x=nodesBuffer.get(iid);
			y=nodesBuffer.get(iid+1);
			z=nodesBuffer.get(iid+2);			
			count++;
			amibeToUNV.put(nodesID[i], count);
			out.println(count+"         1         1         1");
			out.println(x+" "+y+" "+z);			
		}
		out.println("    -1");		
		fc.close();
		fis.close();
		clean(bb);		
	}
	
	/**
	 * @param out
	 * @param amibeNodeToUNVNode
	 */
	private void writeUNVTriangles(PrintStream out, int[] triangles,
		TIntIntHashMap amibeNodeToUNVNode, TIntIntHashMap amibeTriaToUNVTria)
	{
		out.println("    -1"+cr+"  2412");
		int count=0;
		int triaIndex=0;
		for(int i=0; i<groups.length; i++)
		{
			for(int j=0; j<groups[i].length; j++)
			{
				count++;
				amibeTriaToUNVTria.put(groups[i][j], count);
				out.println(""+count+"        91         1         1         1         3");
				out.println(""+amibeNodeToUNVNode.get(triangles[triaIndex++])+" "
					+amibeNodeToUNVNode.get(triangles[triaIndex++])+" "
					+amibeNodeToUNVNode.get(triangles[triaIndex++]));
			}
		}		
		out.println("    -1");
	}
}
