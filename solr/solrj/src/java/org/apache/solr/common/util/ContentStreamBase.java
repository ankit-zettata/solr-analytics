/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.solr.common.util;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.Locale;

import org.apache.commons.io.IOUtils;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.VFS;

/**
 * Three concrete implementations for ContentStream - one for File/URL/String
 * 
 *
 * @since solr 1.2
 */
public abstract class ContentStreamBase implements ContentStream
{
  public static final String DEFAULT_CHARSET = "utf-8";
  
  protected String name;
  protected String sourceInfo;
  protected String contentType;
  protected Long size;
  private Reader decoder;
  private BufferedReader buffered;
  
  //---------------------------------------------------------------------
  //---------------------------------------------------------------------
  
  public static String getCharsetFromContentType( String contentType )
  {
    if( contentType != null ) {
      int idx = contentType.toLowerCase(Locale.ROOT).indexOf( "charset=" );
      if( idx > 0 ) {
        return contentType.substring( idx + "charset=".length() ).trim();
      }
    }
    return null;
  }
  
  //------------------------------------------------------------------------
  //------------------------------------------------------------------------
  
  /**
   * Creates a new content stream from an apache virtual file system
   * source (which allows reading compressed files).
   */
 public static class VFSStream extends ContentStreamBase {
    
    private final String vfsUri;
    private static volatile FileSystemManager fsManager = null;
    private static final Object lock = new Object();
    
    public VFSStream(String vfsUri) {
      this.vfsUri = vfsUri;
      sourceInfo = "vfs";
    }
    
    FileObject fileObject = null;
    InputStream gzipStream = null;
    

    public InputStream getStream() throws IOException {
        if (fsManager == null) {
          synchronized (lock) {
            if (fsManager == null) {
              fsManager = VFS.getManager();
            }
          }        
        }
        
        fileObject = fsManager.resolveFile(this.vfsUri);
        gzipStream = fileObject.getContent().getInputStream();
        return gzipStream;
    }

    @Override
    public void close() {
      super.close();
      if (gzipStream != null) {
        IOUtils.closeQuietly(gzipStream);
      }
      if (fileObject != null) {
        try {
          fileObject.close();
        } catch (FileSystemException e) {
          // ignore
        }
      }
    }
  }
  
  
  /**
   * Construct a <code>ContentStream</code> from a <code>URL</code>
   * 
   * This uses a <code>URLConnection</code> to get the content stream
   * @see  URLConnection
   */
  public static class URLStream extends ContentStreamBase
  {
    private final URL url;
    private InputStream stream;
    
    public URLStream( URL url ) {
      this.url = url; 
      sourceInfo = "url";
    }

    @Override
    public InputStream getStream() throws IOException {
      URLConnection conn = this.url.openConnection();
      
      contentType = conn.getContentType();
      name = url.toExternalForm();
      size = new Long( conn.getContentLength() );
      stream = conn.getInputStream();
      return stream;
    }

    @Override
    public void close() {
      super.close();
      IOUtils.closeQuietly(stream);
    }
  }
  
  /**
   * Construct a <code>ContentStream</code> from a <code>File</code>
   */
  public static class FileStream extends ContentStreamBase
  {
    private final File file;
    private FileInputStream stream;
    
    public FileStream( File f ) {
      file = f; 
      
      contentType = null; // ??
      name = file.getName();
      size = file.length();
      sourceInfo = file.toURI().toString();
    }

    @Override
    public String getContentType() {
      if(contentType==null) {
        InputStream stream = null;
        try {
          stream = new FileInputStream(file);
          char first = (char)stream.read();
          if(first == '<') {
            return "application/xml";
          }
          if(first == '{') {
            return "application/json";
          }
        } catch(Exception ex) {
        } finally {
          if (stream != null) try {
            stream.close();
          } catch (IOException ioe) {}
        }
      }
      return contentType;
    }

    @Override
    public InputStream getStream() throws IOException {
      stream = new FileInputStream( file );
      return stream;
    }

    @Override
    public void close() {
      super.close();
      IOUtils.closeQuietly(stream);
    }
  }
  

  /**
   * Construct a <code>ContentStream</code> from a <code>String</code>
   */
  public static class StringStream extends ContentStreamBase
  {
    private final String str;
    private ByteArrayInputStream stream;
    private Reader reader;
    
    public StringStream( String str ) {
      this.str = str; 
      
      contentType = null;
      name = null;
      size = new Long( str.length() );
      sourceInfo = "string";
    }

    @Override
    public String getContentType() {
      if(contentType==null && str.length() > 0) {
        char first = str.charAt(0);
        if(first == '<') {
          return "application/xml";
        }
        if(first == '{') {
          return "application/json";
        }
        // find a comma? for CSV?
      }
      return contentType;
    }

    @Override
    public InputStream getStream() throws IOException {
      stream = new ByteArrayInputStream( str.getBytes(DEFAULT_CHARSET) );
      return stream;
    }

    /**
     * If an charset is defined (by the contentType) use that, otherwise 
     * use a StringReader
     */
    @Override
    public Reader getReader() throws IOException {
      String charset = getCharsetFromContentType( contentType );
      reader = charset == null 
        ? new StringReader( str )
        : new InputStreamReader( getStream(), charset );
      return reader;
    }

    @Override
    public void close() {
      super.close();
      IOUtils.closeQuietly(reader);
      IOUtils.closeQuietly(stream);
    }
  }

  /**
   * Base reader implementation.  If the contentType declares a 
   * charset use it, otherwise use "utf-8".
   */
  public Reader getReader() throws IOException {
    String charset = getCharsetFromContentType( getContentType() );
    decoder = charset == null 
      ? new InputStreamReader( getStream(), DEFAULT_CHARSET )
      : new InputStreamReader( getStream(), charset );
    
      // added for memory management and performance improvements
      // smaller buffer to manage memory pressures better
      buffered = new BufferedReader(decoder,2048);
      return buffered;
  }

  //------------------------------------------------------------------
  // Getters / Setters for overrideable attributes
  //------------------------------------------------------------------

  public String getContentType() {
    return contentType;
  }

  public void setContentType(String contentType) {
    this.contentType = contentType;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Long getSize() {
    return size;
  }

  public void setSize(Long size) {
    this.size = size;
  }

  public String getSourceInfo() {
    return sourceInfo;
  }

  public void setSourceInfo(String sourceInfo) {
    this.sourceInfo = sourceInfo;
  }
  
  public void close() {
    IOUtils.closeQuietly(this.buffered);
    IOUtils.closeQuietly(this.decoder);
  }
}
