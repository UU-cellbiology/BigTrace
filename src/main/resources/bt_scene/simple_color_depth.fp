uniform vec4 colorin;
out vec4 fragColor;
uniform int surfaceRender;
in vec3 posW;
uniform vec3 clipmin;
uniform vec3 clipmax;
uniform int clipactive;

void main()
{

    //ROI clipping
	if(clipactive>0)
	{
		vec3 s = step(clipmin, posW) - step(clipmax, posW);
		if(s.x * s.y * s.z == 0.0)
		{
			discard;
		}
	}
	//"silhouette" surface
	if(surfaceRender==3)
	{
		gl_FragDepth = 1.0;										
	}
	else
	{
		gl_FragDepth = gl_FragCoord.z;
	}
	fragColor = colorin;
    
}
