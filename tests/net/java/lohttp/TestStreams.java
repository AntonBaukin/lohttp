package net.java.lohttp;

/* Java */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Random;

/* support */

import net.java.lohttp.Support.BytesStream;
import net.java.lohttp.Support.CharBytes;
import net.java.lohttp.Support.TakeBytes;


/**
 * Tests various supports classes separately.
 *
 * @author anton.baukin@gmail.com
 */
public class TestStreams
{
	public void testBytesStream()
	  throws Exception
	{
		Random gen = new Random();

		for(int cycle = 0;(cycle < 512);cycle++)
		{
			ByteArrayOutputStream bos;

			//~: allocate random buffer
			byte[] src = new byte[gen.nextInt(1024 * 512 + 1)];
			gen.nextBytes(src);

			try(BytesStream bs = new BytesStream())
			{
				//~: write data in various chunks
				for(int o = 0, s = src.length;(s > 0);)
				{
					int x = gen.nextInt(s + 1);

					if(gen.nextBoolean())
						bs.write(new ByteArrayInputStream(src, o, x));
					else
						bs.write(src, o, x);

					o += x; s -= x;
				}

				//?: {everything is written}
				EX.assertx(Arrays.equals(src, bs.bytes()));

				//~: check copy operation
				bs.copy(bos = new ByteArrayOutputStream(src.length));
				EX.assertx(Arrays.equals(src, bos.toByteArray()));

				//~: check whole input stream
				bos = new ByteArrayOutputStream(src.length);
				Support.pump(bs.inputStream(), bos);
				EX.assertx(Arrays.equals(src, bos.toByteArray()));

				//~: test random sub-streams
				for(int cycle2 = 0;(cycle2 < 128);cycle2++)
				{
					int xo = gen.nextInt(src.length + 16);
					int xl = gen.nextInt(src.length + 16);
					int  b = Math.min(src.length, xo);
					int  e = Math.min(src.length, xo + xl);

					//~: copy the bytes
					final ByteArrayOutputStream bos2 =
					  new ByteArrayOutputStream(e - b + 1);

					if(gen.nextBoolean())
						Support.pump(bs.inputStream(xo, xl), bos2);
					else
						bs.each(xo, xl, (buf, off, len) -> {
							bos2.write(buf, off, len);
							return true;
						});

					byte[] a = bos2.toByteArray();

					//?: {are length equal}
					EX.assertx(a.length == e - b);

					//?: {are they equal}
					for(int i = 0;(b + i < e);i++)
						if(a[i] != src[b + i])
							throw EX.ass();
				}
			}
		}
	}

	public void testCharBytes()
	  throws Exception
	{
		Random   gen = new Random();

		//~: strings with simple and complex characters.
		String[] CPs = new String[] {
		  "a", "ж", "\u0928\u093F\u4E9C", "\uD800\uDC83"
		};

		//~: random tests
		for(int i = 0;(i < 1024);i++)
		{
			int n = gen.nextInt(1024);

			StringBuilder s = new StringBuilder(n * 4);

			for(int j = 0;(j < n);j++)
				s.append(CPs[gen.nextInt(CPs.length)]);

			//~: canonical conversion
			byte[] tst = s.toString().getBytes("UTF-8");

			//~: convert via the stream
			CharBytes cb = new CharBytes(1 + gen.nextInt(16), s);
			ByteArrayOutputStream bos = new ByteArrayOutputStream(4096);
			Support.pump(cb, bos);

			//?: {are the results equal}
			EX.assertx(Arrays.equals(tst, bos.toByteArray()));
		}
	}


	/* public: test entry point */

	public static void main(String[] argv)
	  throws Exception
	{
		new TestStreams().testBytesStream();
		new TestStreams().testCharBytes();
	}
}