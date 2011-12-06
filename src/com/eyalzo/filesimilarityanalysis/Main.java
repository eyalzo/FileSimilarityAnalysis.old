/**
 * Copyright 2011 Eyal Zohar. All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 * following disclaimer in the documentation and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY EYAL ZOHAR ''AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * EYAL ZOHAR OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
 * TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * 
 * The views and conclusions contained in the software and documentation are those of the authors and should not be
 * interpreted as representing official policies, either expressed or implied, of Eyal Zohar.
 */
package com.eyalzo.filesimilarityanalysis;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

import com.eyalzo.common.chunks.ChunksFiles;
import com.eyalzo.common.chunks.PackChunking;
import com.eyalzo.common.files.FileUtils;

/**
 * @author Eyal Zohar
 */
public class Main
{
	private static final long	MIN_FILE_SIZE	= 1000;
	private static final long	MAX_FILE_SIZE	= 1000000000;
	private static final int	FILE_BLOCK_SIZE	= 1000000;

	private static List<Long> getFileChunks(String fileName, ByteBuffer buffer, PackChunking pack)
	{
		LinkedList<Long> curChunkList = new LinkedList<Long>();
		long curOffset = 0;

		// File blocks loop
		while (true)
		{
			buffer.position(0);
			buffer.limit(buffer.capacity());
			int readBytes = FileUtils.readBlock(buffer, fileName, curOffset, null);
			if (readBytes == 0)
				break;
			// Add the chunks to the list
			int offsetNext = pack.getChunks(curChunkList, buffer.array(), buffer.arrayOffset(), buffer.arrayOffset()
					+ readBytes, false);

			if (readBytes < buffer.capacity())
				break;

			// Next file offset
			curOffset += offsetNext;
		}

		return curChunkList;
	}

	/**
	 * @param args
	 *            Command line arguments.
	 */
	public static void main(String[] args)
	{
		//
		// Get dir name from command line
		//
		if (args.length < 2)
		{
			printUsage();
			System.exit(-1);
		}

		String dirName = args[0];
		int maskBits = Integer.parseInt(args[1]);
		if (maskBits < PackChunking.MIN_MASK_BITS || maskBits > PackChunking.MAX_MASK_BITS)
		{
			printUsage();
			System.exit(-1);
		}

		System.out.println("Directory: " + dirName);
		System.out.println("Mask bits: " + maskBits + " (avg=min+" + Math.pow(2, maskBits) + ")");

		//
		// List files in dir
		//
		List<String> fileList = FileUtils.getDirFileListSorted(dirName, MIN_FILE_SIZE, MAX_FILE_SIZE);
		if (fileList == null || fileList.isEmpty())
		{
			System.out.println("No files in dir \"" + dirName + "\"");
			System.exit(-2);
		}

		// Global chunk list
		ChunksFiles chunksFiles = new ChunksFiles();

		//
		// Process all files
		//
		PackChunking pack = new PackChunking(maskBits);
		ByteBuffer buffer = ByteBuffer.allocate(FILE_BLOCK_SIZE);
		for (String curFileName : fileList)
		{
			File file = new File(curFileName);

			// Get all the file's chunks
			List<Long> curChunkList = getFileChunks(curFileName, buffer, pack);

			// Count overlapping bytes
			long overlappingBytes = chunksFiles.getOverlapsSize(curChunkList);
			double overlapRatio = file.length() <= 0 ? 0 : (overlappingBytes * 100.0) / file.length();

			System.out.println(String.format("%s: %,d bytes %,d chunks %.1f%% overlap", curFileName, file.length(),
					curChunkList.size(), overlapRatio));

			// Print overlaps, if any
			if (overlappingBytes > 0)
				chunksFiles.printOverlaps(curChunkList, 10);

			// Add to the global list
			int newChunks = chunksFiles.addFile(file, curChunkList);
		}
	}

	private static void printUsage()
	{
		System.out.println("Usage: <dir-name> <chunk-bits (7-15)>");
	}
}
