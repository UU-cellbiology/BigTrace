package bigtrace.math;

import java.util.ArrayList;
import java.util.List;

public class NeighborsHashMap
{

	/** for each voxel in the 26 neighborhood
	 * it maps adjacent voxels
	 */
	 public static void fill(final List<int[]>[] neighborsArr)
		{
			//int nCount = 0;
			for (int d1 = -1; d1 < 2; d1++)
			{
				for ( int d2 = -1; d2 < 2; d2++ )
				{
					for ( int d3 = -1; d3 < 2; d3++ )
					{
						//let's determine "neighbor type"
						int nType = Math.abs(d1)+Math.abs(d2)+Math.abs(d3);
						//remove central pixel
						if(nType != 0)
						{
							//let's make a hash string
							//String sKey = Integer.toString(d1)+Integer.toString(d2)+Integer.toString(d3);
							ArrayList<int[]> around =new ArrayList<>();
							//System.out.print(sKey);
							
							//nCount++;	

							//iterating around the neighborhood of each pixel
							for (int m1 = -1;m1 < 2; m1++)
							{
								for ( int m2 = -1; m2 < 2; m2++ )
								{
									for ( int m3 = -1; m3 < 2; m3++ )
									{
										int [] nb = new int [3];
										//"face cross" neighbor
										if(nType == 1)
										{
											nb[0] = m1 + d1*2;
											nb[1] = m2 + d2*2;
											nb[2] = m3 + d3*2;
										}
										else
										{
											nb[0] = m1 + d1;
											nb[1] = m2 + d2;
											nb[2] = m3 + d3;								
										}
										//check if it belongs to the original neighborhood
										
										if(Math.abs(nb[0])<2 && Math.abs(nb[1])<2 && Math.abs(nb[2])<2)
										{
											switch(nType)
											{
												//face, central neighbor
												case 1:
													around.add(nb);
													break;
												//neighbor and the middle of the edge
												case 2:
													//(let's remove central pixel)
													if(Math.abs(nb[0])+Math.abs(nb[1])+Math.abs(nb[2])!=0)
													{
														//remove sides
														if(Math.abs(m1)+Math.abs(m2)+Math.abs(m3)<3)
														{
															around.add(nb);
														}
													}
													break;
												//corner neighbor (let's remove central pixel)
												case 3:
													if(Math.abs(nb[0])+Math.abs(nb[1])+Math.abs(nb[2])!=0)
													{
														around.add(nb);
													}
													break;
												
											}
												
										}
									}
								}
							}
							//neighborsMap.put(sKey, around);							
							neighborsArr[(d1 + 1) * 9 + (d2 + 1) * 3 + (d3 + 1)] = around;
							//System.out.println(" "+around.size()+" type"+Integer.toString(nType));
						}
					}
				}
			}
			//System.out.println(nCount);
			/*
			ArrayList<int[]> test = neighborsMap.get("0-11");
			for(int d=0;d<test.size();d++)
			{
				int[] vals=test.get(d);
				//System.out.println(Integer.toString(vals[0])+Integer.toString(vals[1])+Integer.toString(vals[2]));
				double [] origvector = new double[3];
				origvector[0] = 0.0;
				origvector[1] = -1.0;
				origvector[2] = 1.0;
				LinAlgHelpers.normalize(origvector);
				double [] neighbor = new double [3];
				for(int i=0;i<3;i++)
				{
					neighbor[i] = vals[i];
				}
				LinAlgHelpers.normalize(neighbor);
				//LinAlgHelpers.scale(neighbor, LinAlgHelpers.length(neighbor), neighbor);
				System.out.println(LinAlgHelpers.dot(origvector, neighbor));
				
			}
			*/
			
		}
}
