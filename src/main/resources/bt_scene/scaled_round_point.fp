out vec4 fragColor;

uniform vec4 colorin;
uniform vec2 ellipseAxes;
uniform int renderType;
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
	
    //transform coordinates to NDC
	vec2 coord = 2.0 * gl_PointCoord - 1.0;
	
	
	//ellipse taking into account stretched render window	
	float norm = (coord.x*coord.x*ellipseAxes.x)+(coord.y*coord.y*ellipseAxes.y);		
	
	//cut off everything outside the ellipse
	if ( norm > 1) discard;
	
	//draw only outline,
	//i.e. discard inside
	if(renderType<2)
	{
		if ( norm < 0.6) 
			discard;
	}

	

    fragColor = colorin; 
}