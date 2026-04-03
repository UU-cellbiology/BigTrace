uniform vec4 colorin;
out vec4 fragColor;
in vec3 posW;
uniform vec3 clipmin;
uniform vec3 clipmax;
uniform int clipactive;
uniform int wOIT;

void main()
{    
    //ROI clipping
	if(clipactive > 0)
	{
		vec3 s = step(clipmin, posW) - step(clipmax, posW);
		if(s.x * s.y * s.z == 0.0)
		{
			discard;
		}
	}
	
	vec4 colorout = vec4(colorin);

	if(wOIT > 0)
	{
		colorout.a = colorout.a * exp(-gl_FragCoord.z * 0.8);
		colorout.xyz = colorout.xyz*colorout.a;
	}

    fragColor = colorout; 
	
}
