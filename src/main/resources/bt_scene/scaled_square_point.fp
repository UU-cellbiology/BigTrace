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
	
	
	
	//rectangle 
	float norm = step(1/sqrt(ellipseAxes.x),abs(coord.x)) + step(1/sqrt(ellipseAxes.y),abs(coord.y)); 
	
	//cut off everything outside the rectangle
	if ( norm > 0.5) discard;
	
	//draw only outline
	//i.e. discard inside
	if(renderType<2)
	{
		float norm2 = step(0.8/sqrt(ellipseAxes.x),abs(coord.x)) + step(0.8/sqrt(ellipseAxes.y),abs(coord.y)); 
		if ( norm2 < 0.5) discard;
	}

	

    fragColor = colorin; 
}
