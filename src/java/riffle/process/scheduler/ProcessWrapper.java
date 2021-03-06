/*
 * Copyright (c) 2010 Concurrent, Inc. All Rights Reserved.
 *
 * Project and contact information: http://www.concurrentinc.com/
 */

package riffle.process.scheduler;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import riffle.process.DependencyIncoming;
import riffle.process.DependencyOutgoing;
import riffle.process.Process;
import riffle.process.ProcessCleanup;
import riffle.process.ProcessComplete;
import riffle.process.ProcessPrepare;
import riffle.process.ProcessStart;
import riffle.process.ProcessStop;

/**
 * Class ProcessWrapper wraps an Object instance that has the {@link riffle.process.Process} class Annotation and all relevant method
 * Annotations.
 * <p/>
 * It is generally unnecessary for users to use this class directly. {@link ProcessChain} will automatically wrap
 * process objects with an instance of this class.
 *
 * @see ProcessChain
 * @see riffle.process.Process
 * @see ProcessPrepare
 * @see ProcessStart
 * @see riffle.process.ProcessStop
 * @see riffle.process.ProcessComplete
 * @see ProcessCleanup
 * @see riffle.process.DependencyOutgoing
 * @see riffle.process.DependencyIncoming
 */
public class ProcessWrapper implements Serializable
  {
  Object process;

  public ProcessWrapper( Object process )
    {
    if( process == null )
      throw new IllegalArgumentException( "process argument may not be null" );

    this.process = process;

    verifyObjectIsProcess( process );
    }

  private void verifyObjectIsProcess( Object process )
    {
    riffle.process.Process annotation = process.getClass().getAnnotation( Process.class );

    if( annotation == null )
      throw new IllegalArgumentException( "given process instance must declare the Process annotation" );

    findMethodWith( DependencyOutgoing.class, false );
    findMethodWith( DependencyIncoming.class, false );
    findMethodWith( ProcessComplete.class, false );
    findMethodWith( ProcessStop.class, false );
    }

  public Object getDependencyOutgoing() throws ProcessException
    {
    return findInvoke( DependencyOutgoing.class, false );
    }

  public Object getDependencyIncoming() throws ProcessException
    {
    return findInvoke( DependencyIncoming.class, false );
    }

  /**
   * Method hasPrepare returns true if the annotated child Process object implements the {@link ProcessPrepare} method.
   *
   * @return boolean
   */
  public boolean hasPrepare()
    {
    return findMethodWith( ProcessPrepare.class, true ) != null;
    }

  /**
   * Method prepare calls the annotated child Process object {@link ProcessPrepare} method.
   * <p/>
   * This method will not fail if the prepare method is not implemented.
   *
   * @throws ProcessException when
   */
  public void prepare() throws ProcessException
    {
    findInvoke( ProcessPrepare.class, true );
    }

  /**
   * Method hasCleanup returns true if the annotated child Process object implements the {@link ProcessCleanup} method.
   *
   * @return boolean
   */
  public boolean hasCleanup()
    {
    return findMethodWith( ProcessCleanup.class, true ) != null;
    }

  /**
   * Method cleanup calls the annotated child Process object {@link ProcessCleanup} method.
   * <p/>
   * This method will not fail if the cleanup method is not implemented.
   *
   * @throws ProcessException when
   */
  public void cleanup() throws ProcessException
    {
    findInvoke( ProcessCleanup.class, true );
    }

  /**
   * Method hasStart returns true if the annotated child Process object implements the {@link ProcessStart} method.
   *
   * @return boolean
   */
  public boolean hasStart()
    {
    return findMethodWith( ProcessStart.class, true ) != null;
    }

  /**
   * Method start calls the annotated child Process object {@link ProcessStart} method.
   * <p/>
   * This method will throw an exception if the start method is not implemented. Call {@link #hasStart()}
   * to verify before calling.
   *
   * @throws ProcessException when there is a failure
   */
  public void start() throws ProcessException
    {
    findInvoke( ProcessStart.class, true );
    }

  public void complete() throws ProcessException
    {
    findInvoke( ProcessComplete.class, false );
    }

  public void stop() throws ProcessException
    {
    findInvoke( ProcessStop.class, false );
    }

  private Object findInvoke( Class<? extends Annotation> type, boolean isOptional ) throws ProcessException
    {
    Method method = null;

    try
      {
      method = findMethodWith( type, isOptional );

      if( method == null )
        return null;

      return invokeMethod( method );
      }
    catch( InvocationTargetException exception )
      {
      throw new ProcessException( type, method, exception );
      }
    }

  private Object invokeMethod( Method method ) throws InvocationTargetException
    {
    try
      {
      return method.invoke( process );
      }
    catch( IllegalAccessException exception )
      {
      throw new IllegalStateException( "unable to invoke method: " + method.getName(), exception );
      }
    catch( InvocationTargetException exception )
      {
      if( exception.getCause() instanceof Error )
        throw (Error) exception.getCause();

      if( exception.getCause() instanceof RuntimeException )
        throw (RuntimeException) exception.getCause();

      throw exception;
      }
    }

  private Method findMethodWith( Class<? extends Annotation> type, boolean isOptional )
    {
    Method[] methods = process.getClass().getMethods();

    for( Method method : methods )
      {
      if( method.getAnnotation( type ) == null )
        continue;

      int modifiers = method.getModifiers();

      if( Modifier.isAbstract( modifiers ) )
        throw new IllegalStateException( "given process method: " + method.getName() + " must not be abstract" );

      if( Modifier.isInterface( modifiers ) )
        throw new IllegalStateException( "given process method: " + method.getName() + " must be implemented" );

      if( !Modifier.isPublic( modifiers ) )
        throw new IllegalStateException( "given process method: " + method.getName() + " must be public" );

      return method;
      }

    if( !isOptional )
      throw new IllegalStateException( "no method found declaring annotation: " + type.getName() );

    return null;
    }

  public String toString()
    {
    try
      {
      return getDependencyIncoming() + "->" + getDependencyOutgoing() + ":" + process.toString();
      }
    catch( ProcessException exception )
      {
      throw new IllegalStateException( "unable to get source or sink", exception.getCause() );
      }
    }

  }
