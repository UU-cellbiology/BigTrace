package bigtrace.math;


import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import net.imglib2.view.composite.RealComposite;
/** Eigenvalues and eigenvectors of a real matrix. 
<P>
    If A is symmetric, then A = V*D*V' where the eigenvalue matrix D is
    diagonal and the eigenvector matrix V is orthogonal.
    I.e. A = V.times(D.times(V.transpose())) and 
    V.times(V.transpose()) equals the identity matrix.
<P>
    If A is not symmetric, then the eigenvalue matrix D is block diagonal
    with the real eigenvalues in 1-by-1 blocks and any complex eigenvalues,
    lambda + i*mu, in 2-by-2 blocks, [lambda, mu; -mu, lambda].  The
    columns of V represent the eigenvectors in the sense that A*V = V*D,
    i.e. A.times(V) equals V.times(D).  The matrix V may be badly
    conditioned, or even singular, so the validity of the equation
    A = V*D*inverse(V) depends upon V.cond().
 * @param <T>
**/

public class EigenValVecSymmDecomposition < T extends RealType< T > >
{

/* ------------------------
   Class variables
 * ------------------------ */

   /**
	 * 
	 */
	//private static final long serialVersionUID = -2036425524286417428L;

	/** Row and column dimension (square matrix).
   @serial matrix dimension.
   */
   private int n;

   /** Arrays for internal storage of eigenvalues.
   @serial internal storage of eigenvalues.
   */
   private final double[] dEV, eEV;

   /** Array for internal storage of eigenvectors.
   @serial internal storage of eigenvectors.
   */
   //RealCompositeSymmetricMatrix< T > Vm;
   private final double[][] V;


/* ------------------------
   Constructors
 * ------------------------ */

   /** Default constructor allocating memory for given dimentionality
   @param nDim   Number of dimensions
   */
   public EigenValVecSymmDecomposition( final int nDim )
   {
	   n = nDim;
	   V = new double[n][n];
       dEV = new double[n];
       eEV = new double[n];
   }
   
   private void computeVWRAIthreaded( final RandomAccessibleInterval< T > RAIin, 
		   final RandomAccessibleInterval< T > eVector, 
		   final RandomAccessibleInterval< T > eWeight)
   {
	   EigenValVecSymmDecomposition<T> dCalc = new EigenValVecSymmDecomposition<>(eWeight.numDimensions());
	   
	   dCalc.computeVWRAI(  RAIin, eVector, eWeight);
   }
   
   public void computeVWRAI( final RandomAccessibleInterval< T > RAIin, final RandomAccessibleInterval< T > eVector, final RandomAccessibleInterval< T > eWeight)
   {
	   final Cursor< RealComposite< T > > m =  Views.collapseReal( RAIin ).cursor();
	   final Cursor< RealComposite< T > > eV =  Views.collapseReal( eVector ).cursor();
	   final Cursor<  T > eW =   eWeight.cursor();

	   while ( m.hasNext() )
	   {

		   computeVectorWeight(m.next(),eV.next(),eW.next());
	   }
   }
   
   @SuppressWarnings("rawtypes")
public void computeVWRAI( final RandomAccessibleInterval< T > tensor, 
		   final RandomAccessibleInterval< T > eVector, 
		   final RandomAccessibleInterval< T > eWeight,
		   final int nTasks,
		   final ExecutorService es )
   {
	   	assert nTasks > 0: "Passed nTasks < 1";

		final int tensorDims = tensor.numDimensions();

		long dimensionMax = Long.MIN_VALUE;
		long dimensionMin = Long.MAX_VALUE;
		int dimensionArgMax = -1;

		for ( int d = 0; d < tensorDims - 1; ++d )
		{
			final long size = tensor.dimension( d );
			if ( size > dimensionMax )
			{
				dimensionMax = size;
				dimensionArgMax = d;
				dimensionMin = tensor.min(d);
			}
		}

		final long stepSize = Math.max( dimensionMax / nTasks, 1 );
		final long stepSizeMinusOne = stepSize - 1;
		final long max = dimensionMin+dimensionMax - 1;
		//final ArrayList< Callable< RandomAccessibleInterval< T > > > tasks = new ArrayList<>();
		final ArrayList< Runnable > tasks_r = new ArrayList<>();
		for ( long currentMin = dimensionMin; currentMin < (dimensionMax+dimensionMin); currentMin += stepSize )
		{
			final long currentMax = Math.min( currentMin + stepSizeMinusOne, max );
			final long[] minT = new long[ tensorDims ];
			final long[] maxT = new long[ tensorDims ];
			final long[] minE = new long[ tensorDims ];
			final long[] maxE = new long[ tensorDims ];
			final long[] minW = new long[ tensorDims -1];
			final long[] maxW = new long[ tensorDims -1];
			tensor.min( minT );
			tensor.max( maxT );
			eVector.min( minE );
			eVector.max( maxE );
			eWeight.min( minW );
			eWeight.max( maxW );
			minW[ dimensionArgMax ] = minE[ dimensionArgMax ] = minT[ dimensionArgMax ] = currentMin;
			maxW[ dimensionArgMax ] = maxE[ dimensionArgMax ] = maxT[ dimensionArgMax ] = currentMax;
			
			final IntervalView< T > currentTensor = Views.interval( tensor, new FinalInterval( minT, maxT ) );
			final IntervalView< T > currentEigenVector = Views.interval( eVector, new FinalInterval( minE, maxE ) );
			final IntervalView< T > currentWeights = Views.interval( eWeight, new FinalInterval( minW, maxW ) );
			
			//tasks_r.add( () -> computeRAI( currentTensor, currentEigenVector, currentWeights ) );
			//tasks_r.add( () -> computeRAI( currentTensor, currentEigenVector, currentWeights, false ) );
			tasks_r.add( () -> computeVWRAIthreaded( currentTensor, currentEigenVector, currentWeights) );
		}
		

		Collection < Future  > futures = new HashSet<>();
		for (Runnable r : tasks_r) {

			futures.add(es.submit(r));
		}
	    for(Future future : futures) 
	    {
	        try {
	            future.get();
	        } catch (InterruptedException e) {
	            // Figure out if the interruption means we should stop.
	        	e.printStackTrace();
	        } catch (ExecutionException e) {
				// print error
				e.printStackTrace();
			}
	    }
		
   }
   
   public void computeVWCRAI( final RandomAccessibleInterval< T > RAIin, final RandomAccessibleInterval< T > eVector, final RandomAccessibleInterval< T > eWeight,final RandomAccessibleInterval< T > eCorners)
   {
	   final Cursor< RealComposite< T > > m = Views.collapseReal( RAIin ).cursor();
	   final Cursor< RealComposite< T > > eV = Views.collapseReal( eVector ).cursor();
	   final Cursor<  T > eW = eWeight.cursor();
	   final Cursor<  T > eC =   eCorners.cursor();

	   while ( m.hasNext() )
	   {

		   computeVectorWeightCorners(m.next(),eV.next(),eW.next(),eC.next());
	   }
   }
   
   private void computeVWCRAIthreaded( final RandomAccessibleInterval< T > RAIin, 
		   final RandomAccessibleInterval< T > eVector, 
		   final RandomAccessibleInterval< T > eWeight,
		   final RandomAccessibleInterval< T > eCorners)
   {
	   EigenValVecSymmDecomposition<T> dCalc = new EigenValVecSymmDecomposition<>(eWeight.numDimensions());
	   
	   dCalc.computeVWCRAI(  RAIin, eVector, eWeight, eCorners);
   }
   
   @SuppressWarnings("rawtypes")
public void computeVWCRAI( final RandomAccessibleInterval< T > tensor, 
		   final RandomAccessibleInterval< T > eVector, 
		   final RandomAccessibleInterval< T > eWeight,
		   final RandomAccessibleInterval< T > eCorners,
		   final int nTasks,
		   final ExecutorService es )
   {
	   	assert nTasks > 0: "Passed nTasks < 1";

		final int tensorDims = tensor.numDimensions();

		long dimensionMax = Long.MIN_VALUE;
		long dimensionMin = Long.MAX_VALUE;
		int dimensionArgMax = -1;

		for ( int d = 0; d < tensorDims - 1; ++d )
		{
			final long size = tensor.dimension( d );
			if ( size > dimensionMax )
			{
				dimensionMax = size;
				dimensionArgMax = d;
				dimensionMin = tensor.min(d);
			}
		}

		final long stepSize = Math.max( dimensionMax / nTasks, 1 );
		final long stepSizeMinusOne = stepSize - 1;
		final long max = dimensionMin+dimensionMax - 1;
		//final ArrayList< Callable< RandomAccessibleInterval< T > > > tasks = new ArrayList<>();
		final ArrayList< Runnable > tasks_r = new ArrayList<>();
		for ( long currentMin = dimensionMin; currentMin < (dimensionMax+dimensionMin); currentMin += stepSize )
		{
			final long currentMax = Math.min( currentMin + stepSizeMinusOne, max );
			final long[] minT = new long[ tensorDims ];
			final long[] maxT = new long[ tensorDims ];
			final long[] minE = new long[ tensorDims ];
			final long[] maxE = new long[ tensorDims ];
			final long[] minW = new long[ tensorDims -1];
			final long[] maxW = new long[ tensorDims -1];
			final long[] minC = new long[ tensorDims -1];
			final long[] maxC = new long[ tensorDims -1];
			tensor.min( minT );
			tensor.max( maxT );
			eVector.min( minE );
			eVector.max( maxE );
			eWeight.min( minW );
			eWeight.max( maxW );
			eCorners.min( minC );
			eCorners.max( maxC );
			minC[ dimensionArgMax ] = minW[ dimensionArgMax ] = minE[ dimensionArgMax ] = minT[ dimensionArgMax ] = currentMin;
			maxC[ dimensionArgMax ] = maxW[ dimensionArgMax ] = maxE[ dimensionArgMax ] = maxT[ dimensionArgMax ] = currentMax;
			
			final IntervalView< T > currentTensor = Views.interval( tensor, new FinalInterval( minT, maxT ) );
			final IntervalView< T > currentEigenVector = Views.interval( eVector, new FinalInterval( minE, maxE ) );
			final IntervalView< T > currentWeights = Views.interval( eWeight, new FinalInterval( minW, maxW ) );
			final IntervalView< T > currentCorners = Views.interval( eCorners, new FinalInterval( minC, maxC ) );
			
			//tasks_r.add( () -> computeRAI( currentTensor, currentEigenVector, currentWeights ) );
			//tasks_r.add( () -> computeRAI( currentTensor, currentEigenVector, currentWeights, false ) );
			tasks_r.add( () -> computeVWCRAIthreaded( currentTensor, currentEigenVector, currentWeights, currentCorners) );
		}
		

		Collection < Future  > futures = new HashSet<>();
		for (Runnable r : tasks_r) {

			futures.add(es.submit(r));
		}
	    for(Future future : futures) 
	    {
	        try {
	            future.get();
	        } catch (InterruptedException e) {
	            // Figure out if the interruption means we should stop.
	        	e.printStackTrace();
	        } catch (ExecutionException e) {
				// print error
				e.printStackTrace();
			}
	    }
		
   }
   
   
   void setV(final RealComposite< T > tensor)
   {
	   int nCount = 0;
	 
	   for(int i = 0; i < n; i++)
		   for(int j = i; j < n; j++)
		   {
			   V[i][j] = tensor.get(nCount).getRealDouble();
			   V[j][i] = V[i][j];
			   nCount++;
		   }
   }
   
   
   public void computeVectorWeight( final RealComposite< T > tensor, final RealComposite< T > vector, final T weight)
   {
	   setV(tensor);
	   // Do the math
       // Tridiagonalize.
       tred2();
       // Diagonalize.
       tql2();
       
       ////////////////////////////////////////////////
       
       // organize output
       // find the smallest absolute eigenvalue
       // and store corresponding vector
       int index = 0;
       double dMin = Math.abs(dEV[index]);
       for (int i = 1; i < n; i++)
       {
    	   if(Math.abs(dEV[i]) < dMin)
    	   {
    		   index = i;
    		   dMin = Math.abs(dEV[index]);
    	   }
       }
       for (int i = 0; i < n; i++)
       {
    	   vector.get(i).setReal(V[i][index]);
       } 	   
       boolean bBothNegative = true;
       double dWeight = 0.0;
       for (int i = 0; i < n; i++)
       {
    	   if(i != index)
    	   {
    		   if(dEV[i] > 0)
    		   {
    			   bBothNegative = false;
    		   }
    		   else
    		   {
    			   dWeight -= dEV[i];
    		   }
    	   }
       }       
       if(bBothNegative)
       {
    	   weight.setReal(dWeight);
       }
       else
       {
    	   weight.setZero();
       }
       
       ////////////////////////////////////////
   }
   
   public void computeCorners( final RealComposite< T > tensor, final  T corner)
   {
	   setV(tensor);
	   // Do the math
       // Tridiagonalize.
       tred2();
       // Diagonalize.
       tql2();
       
       
       // organize output
       // find the smallest absolute eigenvalue
       // and store corresponding vector
       int index = 0;
       double dMax = dEV[index];
       double dMin = Math.abs(dEV[index]);
       for (int i =1; i < n; i++)
       {
    	   if(Math.abs(dEV[i]) < dMin)
    	   {
    		   index = i;
    		   dMin = Math.abs(dEV[index]);
    	   }
    	   if(dEV[i] > dMax)
    	   {
    		   dMax = dEV[i];
    	   }
       }
       
       if(dMax<0)
       {
    	   corner.setReal(dMin);
       }
       else
       {
    	   corner.setZero();
       }
   }
   
   public void computeVectorWeightCorners( final RealComposite< T > tensor, final RealComposite< T > vector,  final T weight, final T corner)
   {
	   
	   setV(tensor);
	   // Do the math
       // Tridiagonalize.
       tred2();
       // Diagonalize.
       tql2();
       
       // organize output
       // find the smallest absolute eigenvalue
       // and largest eigenvalue
       // and store corresponding vector
       int index = 0;
       double dMax = dEV[index];
       double dMin = Math.abs(dEV[index]);
       for (int i = 1; i<n; i++)
       {
    	   if(Math.abs(dEV[i]) < dMin)
    	   {
    		   index = i;
    		   dMin = Math.abs(dEV[index]);
    	   }
    	   if(dEV[i] > dMax)
    	   {
    		   dMax = dEV[i];
    	   }
       }
       for (int i = 0; i < n; i++)
       {
    	   vector.get(i).setReal(V[i][index]);
       } 	    
       boolean bBothNegative = true;
       double dWeight = 0.0;
       for (int i = 0; i < n; i++)
       {
    	   if(i != index)
    	   {
    		   if(dEV[i]>0)
    		   {
    			   bBothNegative = false;
    		   }
    		   else
    		   {
    			   dWeight -= dEV[i];
    		   }
    		   
    	   }
       }       
       if(bBothNegative)
       {
    	   weight.setReal(dWeight);
       }
       else
       {
    	   weight.setZero();
       }
       if(dMax < 0)
       {
    	   corner.setReal(dMin);
       }
       else
       {
    	   corner.setZero();
       }
   }
   
   public void computeTensor( final RealComposite< T > tensor, final float [][] out_vals)
   {
	   setV(tensor);
       // Tridiagonalize.
       tred2();
 
       // Diagonalize.
       tql2();
              
       System.out.println("eig "+ Double.toString(dEV[0])+" "+Double.toString(dEV[1])+" "+Double.toString(dEV[2]));
	   for(int i = 0; i < n; i++)
		   for(int j = 0; j < n; j++)
		   {
			   //tensor.get((long)(nCount))
			   out_vals[i][j] = (float) V[j][i];
		   }
   }
   
   public float [] getLineDirection()
   {
	   int nIndex = 0;
	   float [] vLineDirection = new float [3];
	   double dMin=Double.MAX_VALUE;
	   int i;
	   for(i=0;i<3;i++)
	   {
		   if(Math.abs(dEV[i])<dMin)
		   {
			   dMin = Math.abs(dEV[i]);
			   nIndex = i;
		   }
	   }
	   System.out.println("index "+Integer.toString(nIndex));
	   for(i=0;i<3;i++)
	   {
		   vLineDirection[i] = (float) V[i][nIndex];
	   }
	   return vLineDirection;
   }

/* ------------------------
   Public Methods
 * ------------------------ */

   /** Return the eigenvector matrix
   @return     V
   */

  /* public Matrix getV () {
      return new Matrix(V,n,n);
   }*/

   /** Return the real parts of the eigenvalues
   @return     real(diag(D))
   */

   public double[] getRealEigenvalues() 
   {
      return dEV;
   }

   /** Return the imaginary parts of the eigenvalues
   @return     imag(diag(D))
   */

   public double[] getImagEigenvalues () 
   {
      return eEV;
   }

   /** Return the block diagonal eigenvalue matrix 
   **/

   /*public Matrix getD () {
      Matrix X = new Matrix(n,n);
      double[][] D = X.getArray();
      for (int i = 0; i < n; i++) {
         for (int j = 0; j < n; j++) {
            D[i][j] = 0.0;
         }
         D[i][i] = d[i];
         if (e[i] > 0) {
            D[i][i+1] = e[i];
         } else if (e[i] < 0) {
            D[i][i-1] = e[i];
         }
      }
      return X;
   }*/
   
   /* ------------------------
   Private Methods
 * ------------------------ */

   // Symmetric Householder reduction to tridiagonal form.

   private void tred2 () {

   //  This is derived from the Algol procedures tred2 by
   //  Bowdler, Martin, Reinsch, and Wilkinson, Handbook for
   //  Auto. Comp., Vol.ii-Linear Algebra, and the corresponding
   //  Fortran subroutine in EISPACK.

      for (int j = 0; j < n; j++) {
         dEV[j] = V[n-1][j];
      }

      // Householder reduction to tridiagonal form.
   
      for (int i = n-1; i > 0; i--) {
   
         // Scale to avoid under/overflow.
   
         double scale = 0.0;
         double h = 0.0;
         for (int k = 0; k < i; k++) {
            scale = scale + Math.abs(dEV[k]);
         }
         if (scale == 0.0) {
            eEV[i] = dEV[i-1];
            for (int j = 0; j < i; j++) {
               dEV[j] = V[i-1][j];
               V[i][j] = 0.0;
               V[j][i] = 0.0;
            }
         } else {
   
            // Generate Householder vector.
   
            for (int k = 0; k < i; k++) {
               dEV[k] /= scale;
               h += dEV[k] * dEV[k];
            }
            double f = dEV[i-1];
            double g = Math.sqrt(h);
            if (f > 0) {
               g = -g;
            }
            eEV[i] = scale * g;
            h = h - f * g;
            dEV[i-1] = f - g;
            for (int j = 0; j < i; j++) {
               eEV[j] = 0.0;
            }
   
            // Apply similarity transformation to remaining columns.
   
            for (int j = 0; j < i; j++) {
               f = dEV[j];
               V[j][i] = f;
               g = eEV[j] + V[j][j] * f;
               for (int k = j+1; k <= i-1; k++) {
                  g += V[k][j] * dEV[k];
                  eEV[k] += V[k][j] * f;
               }
               eEV[j] = g;
            }
            f = 0.0;
            for (int j = 0; j < i; j++) {
               eEV[j] /= h;
               f += eEV[j] * dEV[j];
            }
            double hh = f / (h + h);
            for (int j = 0; j < i; j++) {
               eEV[j] -= hh * dEV[j];
            }
            for (int j = 0; j < i; j++) {
               f = dEV[j];
               g = eEV[j];
               for (int k = j; k <= i-1; k++) {
                  V[k][j] -= (f * eEV[k] + g * dEV[k]);
               }
               dEV[j] = V[i-1][j];
               V[i][j] = 0.0;
            }
         }
         dEV[i] = h;
      }
   
      // Accumulate transformations.
   
      for (int i = 0; i < n-1; i++) {
         V[n-1][i] = V[i][i];
         V[i][i] = 1.0;
         double h = dEV[i+1];
         if (h != 0.0) {
            for (int k = 0; k <= i; k++) {
               dEV[k] = V[k][i+1] / h;
            }
            for (int j = 0; j <= i; j++) {
               double g = 0.0;
               for (int k = 0; k <= i; k++) {
                  g += V[k][i+1] * V[k][j];
               }
               for (int k = 0; k <= i; k++) {
                  V[k][j] -= g * dEV[k];
               }
            }
         }
         for (int k = 0; k <= i; k++) {
            V[k][i+1] = 0.0;
         }
      }
      for (int j = 0; j < n; j++) {
         dEV[j] = V[n-1][j];
         V[n-1][j] = 0.0;
      }
      V[n-1][n-1] = 1.0;
      eEV[0] = 0.0;
   } 

   // Symmetric tridiagonal QL algorithm.
   
   private void tql2 () {

   //  This is derived from the Algol procedures tql2, by
   //  Bowdler, Martin, Reinsch, and Wilkinson, Handbook for
   //  Auto. Comp., Vol.ii-Linear Algebra, and the corresponding
   //  Fortran subroutine in EISPACK.
   
      for (int i = 1; i < n; i++) {
         eEV[i-1] = eEV[i];
      }
      eEV[n-1] = 0.0;
   
      double f = 0.0;
      double tst1 = 0.0;
      double eps = Math.pow(2.0,-52.0);
      for (int l = 0; l < n; l++) {

         // Find small subdiagonal element
   
         tst1 = Math.max(tst1,Math.abs(dEV[l]) + Math.abs(eEV[l]));
         int m = l;
         while (m < n) {
            if (Math.abs(eEV[m]) <= eps*tst1) {
               break;
            }
            m++;
         }
   
         // If m == l, d[l] is an eigenvalue,
         // otherwise, iterate.
   
         if (m > l) {
            int iter = 0;
            do {
               iter = iter + 1;  // (Could check iteration count here.)
   
               // Compute implicit shift
   
               double g = dEV[l];
               double p = (dEV[l+1] - g) / (2.0 * eEV[l]);
               double r = EigenValVecSymmDecomposition.hypot(p,1.0);
               if (p < 0) {
                  r = -r;
               }
               dEV[l] = eEV[l] / (p + r);
               dEV[l+1] = eEV[l] * (p + r);
               double dl1 = dEV[l+1];
               double h = g - dEV[l];
               for (int i = l+2; i < n; i++) {
                  dEV[i] -= h;
               }
               f = f + h;
   
               // Implicit QL transformation.
   
               p = dEV[m];
               double c = 1.0;
               double c2 = c;
               double c3 = c;
               double el1 = eEV[l+1];
               double s = 0.0;
               double s2 = 0.0;
               for (int i = m-1; i >= l; i--) {
                  c3 = c2;
                  c2 = c;
                  s2 = s;
                  g = c * eEV[i];
                  h = c * p;
                  r = EigenValVecSymmDecomposition.hypot(p,eEV[i]);
                  eEV[i+1] = s * r;
                  s = eEV[i] / r;
                  c = p / r;
                  p = c * dEV[i] - s * g;
                  dEV[i+1] = h + s * (c * g + s * dEV[i]);
   
                  // Accumulate transformation.
   
                  for (int k = 0; k < n; k++) {
                     h = V[k][i+1];
                     V[k][i+1] = s * V[k][i] + c * h;
                     V[k][i] = c * V[k][i] - s * h;
                  }
               }
               p = -s * s2 * c3 * el1 * eEV[l] / dl1;
               eEV[l] = s * p;
               dEV[l] = c * p;
   
               // Check for convergence.
   
            } while (Math.abs(eEV[l]) > eps*tst1);
         }
         dEV[l] = dEV[l] + f;
         eEV[l] = 0.0;
      }
     
      // Sort eigenvalues and corresponding vectors.
   
      for (int i = 0; i < n-1; i++) {
         int k = i;
         double p = dEV[i];
         for (int j = i+1; j < n; j++) {
            if (dEV[j] < p) {
               k = j;
               p = dEV[j];
            }
         }
         if (k != i) {
            dEV[k] = dEV[i];
            dEV[i] = p;
            for (int j = 0; j < n; j++) {
               p = V[j][i];
               V[j][i] = V[j][k];
               V[j][k] = p;
            }
         }
      }
   }

   
  public static double hypot(double paramDouble1, double paramDouble2) {
	  double d;
		if (Math.abs(paramDouble1) > Math.abs(paramDouble2)) {
				d = paramDouble2 / paramDouble1;
				d = Math.abs(paramDouble1) * Math.sqrt(1.0D + d * d);
			} else if (paramDouble2 != 0.0D) {
				d = paramDouble1 / paramDouble2;
				d = Math.abs(paramDouble2) * Math.sqrt(1.0D + d * d);
			} else {
				d = 0.0D;
			}
	     return d;
	}
}