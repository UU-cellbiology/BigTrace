out vec4 fragColor;

uniform vec4 colorin;
uniform int surfaceRender;
in vec3 Normal;
in vec3 FragPos;
in vec3 posW;
uniform vec3 clipmin;
uniform vec3 clipmax;
uniform int clipactive;
uniform float silDecay;
uniform int silType;

//const vec3 ObjectColor = vec3(1, 1, 1);


//const vec3 lightColor1 = 0.5 * vec3(0.9, 0.9, 1);
const vec3 lightColor1 = vec3(1.0, 1.0, 1.0);
const vec3 lightDir1 = normalize(vec3(0, -0.2, -1));

const vec3 lightColor2 = 0.5 * vec3(0.1, 0.1, 1);
const vec3 lightDir2 = normalize(vec3(1, 1, 0.5));

const vec3 ambient = vec3(0.1, 0.1, 0.1);

const float specularStrength = 1;


vec3 diffuse(vec3 norm,  vec3 lightDir, vec3 lightColor)
{	
	return max(dot(norm, lightDir), 0.0) * lightColor;
}

vec3 specular(vec3 norm, vec3 viewDir, vec3 lightDir, vec3 lightColor, float shininess, float specularStrength)
{
	vec3 reflectDir = reflect(-lightDir, norm);
	float spec = pow(max(dot(viewDir, reflectDir), 0.0), shininess);
	return specularStrength * spec * lightColor;
}


void main()
{

		vec3 norm = normalize(Normal);
		vec3 viewDir = normalize(-FragPos);
		vec4 colorOut;
		
		//ROI clipping
		if(clipactive>0)
		{
			vec3 s = step(clipmin, posW) - step(clipmax, posW);
			if(s.x * s.y * s.z == 0.0)
			{
				discard;
			}
		}							
		gl_FragDepth = gl_FragCoord.z;
		//plain, shaded or shiny surface
		if(surfaceRender<3)
		{
			//old code from Tobias
			//vec3 l1 = phong( norm, viewDir, lightDir1, lightColor1, 1.0, 1.0 );
			//vec3 l2 = phong( norm, viewDir, lightDir2, lightColor2, 32, 0.5 );
			//fragColor = vec4((ambient + l1 + l2) * colorin.rgb, colorin.a);
			//plain
			if(surfaceRender==0)
			{
			
				fragColor = colorin;
			}	
			else
			{
			//shaded/shiny
				vec3 diff = diffuse(norm,  lightDir1, lightColor1);
				vec3 spec = specular( norm, viewDir, lightDir1, lightColor1, 16.0, 1.0 )*(surfaceRender-1);
				fragColor = vec4((ambient + diff ) * colorin.rgb+spec, colorin.a);
			}	
		}
		//silhouette surface
		else
		{
			float alphax = min(1.0, 1.0-pow(abs(dot(norm,viewDir)),silDecay));
			if(silType<1)
			{
				//all transparent
				fragColor = vec4(colorin.rgb, colorin.a*alphax);
				gl_FragDepth = 1.0;
			}
			else
			{			
				//front culling
				if(dot(norm,viewDir)>0)
					discard;
				fragColor = vec4(colorin.rgb*alphax, colorin.a);	
			}
		}
		
}
